package com.civicconnect.access.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import com.civicconnect.access.domain.Client;
import com.civicconnect.access.dto.ClientContactResponse;
import com.civicconnect.access.dto.ClientPatch;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.ratelimit.LoginAttemptLimiter;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** A requester's own profile (B3) and Management's contact lookup. */
class ClientServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private ClientRepository clients;
    private CurrentUserService currentUser;
    private LoginAttemptLimiter limiter;
    private ClientService service;
    private Client thabo;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        currentUser = mock(CurrentUserService.class);
        limiter = new LoginAttemptLimiter(5, Duration.ofMinutes(15), new MutableClock());
        service = new ClientService(clients, encoder, currentUser, limiter);

        thabo = client(1);
        thabo.setPassword(encoder.encode("Password123!"));
        when(clients.findById(1L)).thenReturn(Optional.of(thabo));
        when(currentUser.get()).thenReturn(requester(1));
    }

    private static ClientPatch patch(String email, String password, String currentPassword) {
        return new ClientPatch(null, null, email, null, null, password, currentPassword);
    }

    @Test
    @DisplayName("Changing the password without the current password is refused on the currentPassword field")
    void passwordChangeNeedsCurrentPassword() {
        ApiException ex = assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> service.updateMe(patch(null, "NewPassword1", null)));
        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("currentPassword");
        assertThat(encoder.matches("Password123!", thabo.getPassword())).as("password unchanged").isTrue();
    }

    @Test
    @DisplayName("A wrong current password is a 400 (not 401) and counts as a failed sign-in for the account")
    void wrongCurrentPassword() {
        for (int i = 0; i < 5; i++) {
            assertApiError(ErrorCode.VALIDATION_FAILED, () -> service.updateMe(patch("new@example.com", null, "guess")));
        }
        assertApiError(ErrorCode.TOO_MANY_REQUESTS, () -> service.updateMe(patch("new@example.com", null, "Password123!")));
        assertThat(thabo.getEmail()).isEqualTo("client1@example.com");
    }

    @Test
    @DisplayName("With the right current password, email and password change")
    void changesWithCurrentPassword() {
        service.updateMe(patch(" New@Example.com ", "NewPassword1", "Password123!"));

        assertThat(thabo.getEmail()).isEqualTo("new@example.com");
        assertThat(encoder.matches("NewPassword1", thabo.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Name and phone changes do not need the current password; re-sending the same email does not either")
    void otherFieldsDoNotNeedPassword() {
        service.updateMe(new ClientPatch("Thabiso", null, "CLIENT1@example.com", "0820000009", null, null, null));

        assertThat(thabo.getFirstName()).isEqualTo("Thabiso");
        assertThat(thabo.getPhone()).isEqualTo("0820000009");
    }

    @Test
    @DisplayName("Management gets contact details only (no date of birth), and an unknown client is 404")
    void contactDetails() {
        when(currentUser.get()).thenReturn(manager(1));
        ClientRepository.ClientContact contact = mock(ClientRepository.ClientContact.class);
        when(contact.getId()).thenReturn(1L);
        when(contact.getEmail()).thenReturn("thabo.mokoena@example.com");
        when(contact.getActive()).thenReturn(true);
        when(clients.findContact(1L)).thenReturn(Optional.of(contact));

        ClientContactResponse response = service.contact(1L);

        assertThat(response.email()).isEqualTo("thabo.mokoena@example.com");
        assertThat(ClientContactResponse.class.getRecordComponents())
                .extracting(c -> c.getName()).doesNotContain("dateOfBirth", "password");
        assertApiError(ErrorCode.NOT_FOUND, () -> service.contact(99L));
    }

    @Test
    @DisplayName("Own profile, taken email, and activating or deactivating a client")
    void profileAndActivation() {
        when(clients.emailInUse("taken@example.com")).thenReturn(true);
        when(clients.updateActive(eq(1L), eq(false), any())).thenReturn(1);

        assertThat(service.me().id()).isEqualTo(1L);
        assertApiError(ErrorCode.CONFLICT, () -> service.updateMe(patch("taken@example.com", null, "Password123!")));
        when(currentUser.get()).thenReturn(manager(1));
        service.setActive(1L, false);
        assertApiError(ErrorCode.NOT_FOUND, () -> service.setActive(99L, true));
    }
}
