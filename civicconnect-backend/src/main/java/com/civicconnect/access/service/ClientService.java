package com.civicconnect.access.service;

import com.civicconnect.common.web.Text;
import com.civicconnect.access.domain.Client;
import com.civicconnect.access.dto.ClientContactResponse;
import com.civicconnect.access.dto.ClientPatch;
import com.civicconnect.access.dto.ClientResponse;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.ratelimit.LoginAttemptLimiter;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;
    private final LoginAttemptLimiter loginLimiter;

    public ClientService(ClientRepository clientRepository, PasswordEncoder passwordEncoder,
                         CurrentUserService currentUser, LoginAttemptLimiter loginLimiter) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.loginLimiter = loginLimiter;
    }

    @Transactional(readOnly = true)
    public ClientResponse me() {
        CurrentUser user = currentUser.get();
        return ClientResponse.from(loadSelf(user));
    }

    @Transactional
    public ClientResponse updateMe(ClientPatch body) {
        CurrentUser user = currentUser.get();
        Client c = loadSelf(user);

        String newEmail = body.email() == null ? null : body.email().trim().toLowerCase(Locale.ROOT);
        boolean emailChanges = newEmail != null && !newEmail.equalsIgnoreCase(c.getEmail());
        if (emailChanges || body.password() != null) {
            confirmCurrentPassword(c, body.currentPassword());
        }

        if (emailChanges) {
            if (clientRepository.emailInUse(newEmail)) {
                throw Errors.conflict(ErrorCode.CONFLICT, "This email address is already in use.");
            }
            c.setEmail(newEmail);
        }
        if (body.firstName() != null) c.setFirstName(body.firstName().trim());
        if (body.surname() != null) c.setSurname(body.surname().trim());
        if (body.phone() != null) c.setPhone(Text.blankToNull(body.phone())); // "" clears it
        if (body.dateOfBirth() != null) c.setDateOfBirth(body.dateOfBirth());
        if (body.password() != null) c.setPassword(passwordEncoder.encode(body.password()));
        c.setUpdatedAt(Time.now());
        return ClientResponse.from(c);
    }

    /**
     * Management: a client's contact details, to contact them about a request. Reads only the
     * contact columns (no date of birth, no password hash) and logs who looked (POPIA accountability -
     * reads are not in the audit trail, which only records changes).
     */
    @Transactional(readOnly = true)
    public ClientContactResponse contact(Long clientId) {
        CurrentUser user = currentUser.get();
        ClientContactResponse contact = clientRepository.findContact(clientId).map(ClientContactResponse::from)
                .orElseThrow(() -> Errors.notFound("Client"));
        log.info("Employee {} viewed the contact details of client {}", user.id(), clientId);
        return contact;
    }

    @Transactional
    public void setActive(Long clientId, boolean active) {
        if (clientRepository.updateActive(clientId, active, Time.now()) == 0) {
            throw Errors.notFound("Client");
        }
    }

    /**
     * B3. A wrong current password is a 400 on the currentPassword field - not a 401, which the web app
     * treats as "session expired" - and counts as a failed sign-in for this account, so it cannot be used
     * to guess the password with a stolen token.
     */
    private void confirmCurrentPassword(Client c, String currentPassword) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw Errors.invalidField("currentPassword", "Enter your current password to change your email or password");
        }
        loginLimiter.checkAllowed(c.getEmail());
        if (!passwordEncoder.matches(currentPassword, c.getPassword())) {
            loginLimiter.recordFailure(c.getEmail());
            throw Errors.invalidField("currentPassword", "Current password is incorrect");
        }
    }

    private Client loadSelf(CurrentUser user) {
        return clientRepository.findById(user.id()).orElseThrow(() -> Errors.notFound("Client"));
    }
}
