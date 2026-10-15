package com.civicconnect.access.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.ratelimit.LoginAttemptLimiter;
import com.civicconnect.common.security.AppRoleOnly;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two database steps of an employee changing their own password, kept in separate service calls so
 * each runs as the right database role (DbSessionInterceptor):
 *  1. check the current password - as the app's login role, because staff may not read password hashes;
 *  2. store the new hash - as the employee's own role, so the audit trail records them as the actor.
 *     Staff may only update the password column of their own row (V6 grant + row level security).
 */
@Service
public class EmployeeCredentials {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginLimiter;

    public EmployeeCredentials(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder,
                               LoginAttemptLimiter loginLimiter) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginLimiter = loginLimiter;
    }

    /**
     * A wrong current password is a 400 on the currentPassword field (not a 401, which the web app treats
     * as "session expired") and counts as a failed sign-in for the account, like a requester's (B3).
     */
    @Transactional(readOnly = true)
    @AppRoleOnly
    public void checkCurrentPassword(Long empId, String currentPassword) {
        Employee e = employeeRepository.findById(empId).orElseThrow(() -> Errors.notFound("Employee"));
        loginLimiter.checkAllowed(e.getEmail());
        if (!passwordEncoder.matches(currentPassword, e.getPassword())) {
            loginLimiter.recordFailure(e.getEmail());
            throw Errors.invalidField("currentPassword", "Current password is incorrect");
        }
    }

    @Transactional
    public void storeNewPassword(Long empId, String rawPassword) {
        if (employeeRepository.updatePassword(empId, passwordEncoder.encode(rawPassword)) == 0) {
            throw Errors.notFound("Employee");
        }
    }
}
