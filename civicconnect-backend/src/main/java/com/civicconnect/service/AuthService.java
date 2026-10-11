package com.civicconnect.service;

import com.civicconnect.error.ErrorCode;
import com.civicconnect.dto.AuthResponse;
import com.civicconnect.dto.ClientResponse;
import com.civicconnect.dto.LoginRequest;
import com.civicconnect.dto.MeResponse;
import com.civicconnect.dto.RegisterRequest;
import com.civicconnect.model.Client;
import com.civicconnect.model.Employee;
import com.civicconnect.repository.ClientRepository;
import com.civicconnect.repository.EmployeeRepository;
import com.civicconnect.security.CurrentUser;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.civicconnect.error.ApiException;

import java.util.Optional;

/**
 * The Access module: registration, login and "who am I" (API-01 to API-03).
 * These run as the base application database user, because nobody is logged in yet when they start.
 */
@Service
public class AuthService {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;

    public AuthService(ClientRepository clientRepository, EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, CurrentUserService currentUser) {
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    /** API-01: a requester creates an account. The password is stored as a BCrypt hash (NFR-002). */
    @Transactional
    public ClientResponse register(RegisterRequest body) {
        String email = body.email().trim().toLowerCase();
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

    /** API-02: checks the email and password and returns a JWT. Employees are checked first. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest body) {
        String email = body.email().trim();

        Optional<Employee> employee = employeeRepository.findByEmailIgnoreCase(email);
        if (employee.isPresent()) {
            Employee e = employee.get();
            checkPassword(body.password(), e.getPassword(), e.isActive());
            CurrentUser.Role role = e.getRole() == Employee.Role.MANAGEMENT
                    ? CurrentUser.Role.MANAGEMENT : CurrentUser.Role.STAFF;
            return token(new CurrentUser(e.getEmpId(), role));
        }

        Client c = clientRepository.findByEmailIgnoreCase(email).orElseThrow(AuthService::invalidLogin);
        checkPassword(body.password(), c.getPassword(), c.isActive());
        return token(new CurrentUser(c.getClientId(), CurrentUser.Role.REQUESTER));
    }

    /** API-03 */
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

    private void checkPassword(String raw, String hash, boolean active) {
        // Same message for every failure so the response does not reveal which emails exist
        if (!active || !passwordEncoder.matches(raw, hash)) {
            throw invalidLogin();
        }
    }

    private AuthResponse token(CurrentUser user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new AuthResponse(issued.token(), issued.expiresAt(), user.role().name());
    }

    private static ApiException invalidLogin() {
        return Errors.unauthenticated("Invalid email or password.");
    }
}
