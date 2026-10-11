package com.civicconnect.service;

import com.civicconnect.error.ErrorCode;
import com.civicconnect.dto.ClientPatch;
import com.civicconnect.dto.ClientResponse;
import com.civicconnect.model.Client;
import com.civicconnect.repository.ClientRepository;
import com.civicconnect.repository.EmployeeRepository;
import com.civicconnect.security.CurrentUser;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Client accounts. Requesters manage their own profile; Management can only activate or deactivate
 * an account and never reads client personal data (ASR-005). Clients are never deleted.
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;
    private final DbSession db;

    public ClientService(ClientRepository clientRepository, EmployeeRepository employeeRepository,
                         PasswordEncoder passwordEncoder, CurrentUserService currentUser, DbSession db) {
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.db = db;
    }

    @Transactional(readOnly = true)
    public ClientResponse me() {
        CurrentUser user = currentUser.get();
        db.begin(user);
        return ClientResponse.from(loadSelf(user));
    }

    @Transactional
    public ClientResponse updateMe(ClientPatch body) {
        CurrentUser user = currentUser.get();
        db.begin(user);
        Client c = loadSelf(user);

        if (body.email() != null) {
            String email = body.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(c.getEmail())) {
                if (clientRepository.existsByEmailIgnoreCase(email) || employeeRepository.existsByEmailIgnoreCase(email)) {
                    throw Errors.conflict(ErrorCode.CONFLICT, "This email address is already in use.");
                }
                c.setEmail(email);
            }
        }
        if (body.firstName() != null) c.setFirstName(body.firstName().trim());
        if (body.surname() != null) c.setSurname(body.surname().trim());
        if (body.phone() != null) c.setPhone(body.phone());
        if (body.dateOfBirth() != null) c.setDateOfBirth(body.dateOfBirth());
        if (body.password() != null) c.setPassword(passwordEncoder.encode(body.password()));
        c.setUpdatedAt(LocalDateTime.now());
        return ClientResponse.from(c);
    }

    /** Management activates or deactivates a client account without reading it. */
    @Transactional
    public void setActive(Long clientId, boolean active) {
        db.begin(currentUser.get());
        if (clientRepository.updateActive(clientId, active, LocalDateTime.now()) == 0) {
            throw Errors.notFound("Client");
        }
    }

    private Client loadSelf(CurrentUser user) {
        return clientRepository.findById(user.id()).orElseThrow(() -> Errors.notFound("Client"));
    }
}
