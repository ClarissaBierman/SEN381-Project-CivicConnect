package com.civicconnect.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B1: a valid token is not enough - the account must still be active and still have the role in the
 * token. Decision table: exists x active x same role.
 */
class ActiveAccountJwtConverterTest {

    private final AccountStatusLookup accounts = mock(AccountStatusLookup.class);
    private final ActiveAccountJwtConverter converter = new ActiveAccountJwtConverter(accounts);

    private static Jwt token(long id, String role) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(id)).claim("role", role).build();
    }

    @Test
    @DisplayName("Active account with the same role: logged in with ROLE_<role>")
    void activeAccount() {
        when(accounts.find(CurrentUser.Role.STAFF, 3)).thenReturn(
                Optional.of(new AccountStatusLookup.AccountState(true, CurrentUser.Role.STAFF)));

        AbstractAuthenticationToken auth = converter.convert(token(3, "STAFF"));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_STAFF");
        assertThat(auth.getName()).isEqualTo("3");
    }

    @Test
    @DisplayName("Deactivated account: refused (401 'account no longer active'), even with an unexpired token")
    void deactivatedAccount() {
        when(accounts.find(CurrentUser.Role.STAFF, 2)).thenReturn(
                Optional.of(new AccountStatusLookup.AccountState(false, CurrentUser.Role.STAFF)));

        assertThatThrownBy(() -> converter.convert(token(2, "STAFF"))).isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("Role changed since login (e.g. a demoted manager): refused, must log in again")
    void roleChanged() {
        when(accounts.find(CurrentUser.Role.MANAGEMENT, 7)).thenReturn(
                Optional.of(new AccountStatusLookup.AccountState(true, CurrentUser.Role.STAFF)));

        assertThatThrownBy(() -> converter.convert(token(7, "MANAGEMENT"))).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    @DisplayName("Account no longer exists, or the token has no valid role: refused")
    void missingAccountOrBadRole() {
        when(accounts.find(CurrentUser.Role.REQUESTER, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(token(99, "REQUESTER"))).isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(token(1, "ADMIN"))).isInstanceOf(InvalidBearerTokenException.class);
    }
}
