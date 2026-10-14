package com.civicconnect.access.service;

import com.civicconnect.access.domain.Client;
import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.dto.AuthResponse;
import com.civicconnect.access.dto.ClientResponse;
import com.civicconnect.access.dto.LoginRequest;
import com.civicconnect.access.dto.MeResponse;
import com.civicconnect.access.dto.RegisterRequest;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.ratelimit.LoginAttemptLimiter;
import com.civicconnect.common.ratelimit.RateLimitedException;
import com.civicconnect.common.ratelimit.TokenBucketLimiter;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.security.JwtService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;
    private final LoginAttemptLimiter loginLimiter;
    private final TokenBucketLimiter registrationLimiter;

    /**
     * A real BCrypt hash of a random value. When the email is unknown, the password is still checked
     * against this, so a wrong email takes as long as a wrong password and response times do not reveal
     * which emails have accounts (B4).
     */
    private final String dummyHash;

    public AuthService(ClientRepository clientRepository, EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, CurrentUserService currentUser,
                       LoginAttemptLimiter loginLimiter,
                       @Qualifier("registrationLimiter") TokenBucketLimiter registrationLimiter) {
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.loginLimiter = loginLimiter;
        this.registrationLimiter = registrationLimiter;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public ClientResponse register(RegisterRequest body) {
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        long retryAfter = registrationLimiter.tryConsume(email);
        if (retryAfter > 0) {
            throw new RateLimitedException("Too many registration attempts for this email address. Please try again later.",
                    retryAfter);
        }
        if (clientRepository.existsByEmailIgnoreCase(email) || employeeRepository.existsByEmailIgnoreCase(email)) {
            throw Errors.conflict(ErrorCode.CONFLICT, "This email address is already registered.");
        }
        Client client = new Client();
        client.setFirstName(body.firstName().trim());
        client.setSurname(body.surname().trim());
        client.setEmail(email);
        client.setPhone(body.phone());
        client.setPassword(passwordEncoder.encode(body.password()));
        return ClientResponse.from(clientRepository.save(client));
    }

    /**
     * B4: locked emails are refused before the password is checked; every failure is counted against
     * the email address (not the IP), and a success clears the count. Unknown emails, wrong passwords
     * and deactivated accounts all cost one BCrypt check and give the same 401 message.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest body) {
        String email = body.email().trim();
        loginLimiter.checkAllowed(email);

        Optional<Employee> employee = employeeRepository.findByEmailIgnoreCase(email);
        if (employee.isPresent()) {
            Employee e = employee.get();
            checkPassword(email, body.password(), e.getPassword(), e.isActive());
            CurrentUser.Role role = e.getRole() == Employee.Role.MANAGEMENT
                    ? CurrentUser.Role.MANAGEMENT : CurrentUser.Role.STAFF;
            return token(new CurrentUser(e.getEmpId(), role));
        }

        Optional<Client> client = clientRepository.findByEmailIgnoreCase(email);
        if (client.isEmpty()) {
            checkPassword(email, body.password(), dummyHash, false);
        }
        Client c = client.get();
        checkPassword(email, body.password(), c.getPassword(), c.isActive());
        return token(new CurrentUser(c.getClientId(), CurrentUser.Role.REQUESTER));
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        CurrentUser user = currentUser.get();
        if (user.isRequester()) {
            Client c = clientRepository.findById(user.id()).orElseThrow(AuthService::invalidLogin);
            return new MeResponse(c.getClientId(), user.role().name(), c.getFirstName(), c.getSurname(), c.getEmail());
        }
        Employee e = employeeRepository.findById(user.id()).orElseThrow(AuthService::invalidLogin);
        return new MeResponse(e.getEmpId(), user.role().name(), e.getFirstName(), e.getSurname(), e.getEmail());
    }

    /** Always runs BCrypt (even for inactive accounts), so timing does not reveal the account state. */
    private void checkPassword(String email, String raw, String hash, boolean active) {
        boolean matches = passwordEncoder.matches(raw, hash);
        if (!matches || !active) {
            loginLimiter.recordFailure(email);
            throw invalidLogin();
        }
        loginLimiter.recordSuccess(email);
    }

    private AuthResponse token(CurrentUser user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new AuthResponse(issued.token(), issued.expiresAt(), user.role().name());
    }

    private static ApiException invalidLogin() {
        return Errors.unauthenticated("Invalid email or password.");
    }
}
