package com.civicconnect.access.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.ratelimit.LoginAttemptLimiter;
import com.civicconnect.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** API-26: an employee's own password change - the current password check and storing the new hash. */
class EmployeeCredentialsTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private EmployeeRepository employees;
    private LoginAttemptLimiter limiter;
    private EmployeeCredentials credentials;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeRepository.class);
        limiter = new LoginAttemptLimiter(2, Duration.ofMinutes(15), new MutableClock());
        credentials = new EmployeeCredentials(employees, encoder, limiter);
        Employee johan = employee(2, Employee.Role.STAFF, true);
        johan.setPassword(encoder.encode("Password123!"));
        when(employees.findById(2L)).thenReturn(Optional.of(johan));
    }

    @Test
    @DisplayName("The right current password passes; a wrong one is a 400 on currentPassword, not a 401")
    void checksCurrentPassword() {
        credentials.checkCurrentPassword(2L, "Password123!");

        ApiException e = assertApiError(ErrorCode.VALIDATION_FAILED, () -> credentials.checkCurrentPassword(2L, "wrong"));
        assertThat(e.getFieldErrors()).singleElement()
                .satisfies(f -> assertThat(f.field()).isEqualTo("currentPassword"));
        assertApiError(ErrorCode.NOT_FOUND, () -> credentials.checkCurrentPassword(99L, "x"));
    }

    @Test
    @DisplayName("Wrong current passwords count as failed sign-ins and lock the account like a login (B3)")
    void wrongPasswordsLockTheAccount() {
        assertApiError(ErrorCode.VALIDATION_FAILED, () -> credentials.checkCurrentPassword(2L, "wrong1"));
        assertApiError(ErrorCode.VALIDATION_FAILED, () -> credentials.checkCurrentPassword(2L, "wrong2"));
        assertApiError(ErrorCode.TOO_MANY_REQUESTS, () -> credentials.checkCurrentPassword(2L, "Password123!"));
    }

    @Test
    @DisplayName("The new password is stored as a BCrypt hash, never in plain text")
    void storesHash() {
        when(employees.updatePassword(eq(2L), anyString())).thenReturn(1);

        credentials.storeNewPassword(2L, "New12345!");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(employees).updatePassword(eq(2L), hash.capture());
        assertThat(hash.getValue()).startsWith("$2").isNotEqualTo("New12345!");
        assertThat(encoder.matches("New12345!", hash.getValue())).isTrue();

        when(employees.updatePassword(anyLong(), anyString())).thenReturn(0);
        assertApiError(ErrorCode.NOT_FOUND, () -> credentials.storeNewPassword(99L, "New12345!"));
    }
}
