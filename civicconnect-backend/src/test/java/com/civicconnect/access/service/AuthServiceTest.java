package com.civicconnect.access.service;

import com.civicconnect.access.domain.Client;
import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.dto.AuthResponse;
import com.civicconnect.access.dto.ClientResponse;
import com.civicconnect.access.dto.LoginRequest;
import com.civicconnect.access.dto.RegisterRequest;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.common.config.SecurityConfig;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.ratelimit.LoginAttemptLimiter;
import com.civicconnect.common.ratelimit.RateLimitedException;
import com.civicconnect.common.ratelimit.TokenBucketLimiter;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.support.MutableClock;
import com.civicconnect.common.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** API-01 and API-02: registration, login and password hashing (NFR-002, DEC-010). */
class AuthServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private ClientRepository clients;
    private EmployeeRepository employees;
    private PasswordEncoder encoder;
    private JwtDecoder decoder;
    private AuthService service;
    private LoginAttemptLimiter loginLimiter;
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        employees = mock(EmployeeRepository.class);
        encoder = new BCryptPasswordEncoder(4); // low cost factor keeps the tests fast

        SecurityConfig config = new SecurityConfig();
        SecretKey key = config.jwtSigningKey(SECRET);
        decoder = config.jwtDecoder(key);
        JwtService jwt = new JwtService(config.jwtEncoder(key), 60);

        loginLimiter = new LoginAttemptLimiter(5, Duration.ofMinutes(15), clock);
        service = new AuthService(clients, employees, encoder, jwt, mock(CurrentUserService.class), loginLimiter,
                new TokenBucketLimiter(5, Duration.ofHours(1), clock));
        when(clients.save(any())).thenAnswer(i -> { Client c = i.getArgument(0); c.setClientId(42L); return c; });
    }

    private Employee employeeWithPassword(long id, Employee.Role role, boolean active, String password) {
        Employee e = employee(id, role, active);
        e.setPassword(encoder.encode(password));
        when(employees.findByEmailIgnoreCase(e.getEmail())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    @DisplayName("Registration stores a BCrypt hash, never the plain password (NFR-002)")
    void registerHashesPassword() {
        ClientResponse response = service.register(
                new RegisterRequest("Lindi", "Zulu", "  Lindi@Example.COM ", null, "Secret123!"));

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isNotEqualTo("Secret123!").startsWith("$2");
        assertThat(encoder.matches("Secret123!", saved.getValue().getPassword())).isTrue();
        assertThat(saved.getValue().getEmail()).isEqualTo("lindi@example.com");
        assertThat(response.id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("An email already used by an employee cannot be registered")
    void registerRejectsDuplicateEmail() {
        when(employees.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertApiError(ErrorCode.CONFLICT,
                () -> service.register(new RegisterRequest("A", "B", "taken@example.com", null, "Secret123!")));
        verify(clients, never()).save(any());
    }

    @Test
    @DisplayName("A manager's login returns a signed token carrying their ID and MANAGEMENT role")
    void managerLoginReturnsToken() {
        Employee e = employeeWithPassword(1, Employee.Role.MANAGEMENT, true, "Password123!");

        AuthResponse response = service.login(new LoginRequest(e.getEmail(), "Password123!"));

        Jwt jwt = decoder.decode(response.token());
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString(JwtService.ROLE_CLAIM)).isEqualTo("MANAGEMENT");
        assertThat(response.role()).isEqualTo("MANAGEMENT");
        // the token stores whole seconds, the response keeps the exact instant
        assertThat(response.expiresAt()).isCloseTo(jwt.getExpiresAt(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("A client's login returns a REQUESTER token")
    void clientLoginReturnsRequesterToken() {
        Client c = client(7);
        c.setPassword(encoder.encode("Password123!"));
        when(clients.findByEmailIgnoreCase(c.getEmail())).thenReturn(Optional.of(c));

        AuthResponse response = service.login(new LoginRequest(c.getEmail(), "Password123!"));

        assertThat(decoder.decode(response.token()).getClaimAsString(JwtService.ROLE_CLAIM)).isEqualTo("REQUESTER");
    }

    @Test
    @DisplayName("A wrong password is rejected")
    void wrongPasswordRejected() {
        Employee e = employeeWithPassword(3, Employee.Role.STAFF, true, "Password123!");

        assertApiError(ErrorCode.UNAUTHENTICATED, () -> service.login(new LoginRequest(e.getEmail(), "wrong")));
    }

    @Test
    @DisplayName("A deactivated account cannot log in, even with the right password")
    void inactiveAccountRejected() {
        Employee e = employeeWithPassword(5, Employee.Role.STAFF, false, "Password123!");

        assertApiError(ErrorCode.UNAUTHENTICATED, () -> service.login(new LoginRequest(e.getEmail(), "Password123!")));
    }

    @Test
    @DisplayName("An unknown email gets the same error as a wrong password (does not reveal which emails exist)")
    void unknownEmailRejected() {
        var ex = assertApiError(ErrorCode.UNAUTHENTICATED,
                () -> service.login(new LoginRequest("nobody@example.com", "Password123!")));
        assertThat(ex.getMessage()).isEqualTo("Invalid email or password.");
    }

    // ------------------------------------------------------------------ B4: brute force and enumeration

    @Test
    @DisplayName("After 5 wrong passwords the email is locked (429) - even the right password is refused until it unlocks")
    void lockedAfterFiveFailures() {
        Employee e = employeeWithPassword(3, Employee.Role.STAFF, true, "Password123!");
        for (int i = 0; i < 5; i++) {
            assertApiError(ErrorCode.UNAUTHENTICATED, () -> service.login(new LoginRequest(e.getEmail(), "wrong")));
        }

        assertApiError(ErrorCode.TOO_MANY_REQUESTS, () -> service.login(new LoginRequest(e.getEmail(), "Password123!")));

        clock.advance(Duration.ofMinutes(15));
        assertThat(service.login(new LoginRequest(e.getEmail(), "Password123!")).token()).isNotBlank();
    }

    @Test
    @DisplayName("Unknown emails are counted and locked the same way, so the lock does not reveal which emails exist")
    void unknownEmailIsLockedToo() {
        for (int i = 0; i < 5; i++) {
            assertApiError(ErrorCode.UNAUTHENTICATED, () -> service.login(new LoginRequest("ghost@example.com", "x")));
        }
        assertApiError(ErrorCode.TOO_MANY_REQUESTS, () -> service.login(new LoginRequest("ghost@example.com", "x")));
    }

    @Test
    @DisplayName("An unknown email still costs one BCrypt check, so it takes as long as a wrong password")
    void unknownEmailRunsBcrypt() {
        PasswordEncoder spy = spy(encoder);
        AuthService timed = new AuthService(clients, employees, spy, mock(JwtService.class), mock(CurrentUserService.class),
                new LoginAttemptLimiter(5, Duration.ofMinutes(15), clock), new TokenBucketLimiter(5, Duration.ofHours(1), clock));

        assertApiError(ErrorCode.UNAUTHENTICATED, () -> timed.login(new LoginRequest("nobody@example.com", "Password123!")));

        verify(spy).matches(eq("Password123!"), startsWith("$2"));
    }

    @Test
    @DisplayName("Registration is limited to 5 attempts per email address per hour")
    void registrationLimitedPerEmail() {
        when(clients.existsByEmailIgnoreCase("spam@example.com")).thenReturn(true);
        for (int i = 0; i < 5; i++) {
            assertApiError(ErrorCode.CONFLICT,
                    () -> service.register(new RegisterRequest("A", "B", "spam@example.com", null, "Secret123!")));
        }
        assertApiError(ErrorCode.TOO_MANY_REQUESTS,
                () -> service.register(new RegisterRequest("A", "B", "SPAM@example.com", null, "Secret123!")));
    }
}
