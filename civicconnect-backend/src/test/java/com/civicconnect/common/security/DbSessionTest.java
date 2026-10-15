package com.civicconnect.common.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.Query;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Each user type maps to the database role created by the Supabase build script. */
class DbSessionTest {

    @Test
    @DisplayName("Every user type has its own database role")
    void rolesMatchBuildScript() {
        assertThat(DbSession.databaseRole(CurrentUser.Role.REQUESTER)).isEqualTo("civicconnect_client");
        assertThat(DbSession.databaseRole(CurrentUser.Role.STAFF)).isEqualTo("civicconnect_staff");
        assertThat(DbSession.databaseRole(CurrentUser.Role.MANAGEMENT)).isEqualTo("civicconnect_management");
    }

    @Test
    @DisplayName("begin sets the actor and switches role; reset clears both; role switching can be turned off")
    void beginAndReset() {
        EntityManager em = mock(EntityManager.class, RETURNS_DEEP_STUBS);
        Query query = mock(Query.class, RETURNS_SELF);
        when(em.createNativeQuery(anyString())).thenReturn(query);

        DbSession switching = new DbSession(true);
        ReflectionTestUtils.setField(switching, "entityManager", em);
        switching.begin(new CurrentUser(3L, CurrentUser.Role.STAFF));
        verify(query).setParameter("empId", "3");
        verify(query).setParameter("role", "civicconnect_staff");
        switching.reset();
        verify(em, times(4)).createNativeQuery(anyString());

        EntityManager em2 = mock(EntityManager.class);
        when(em2.createNativeQuery(anyString())).thenReturn(query);
        DbSession fixed = new DbSession(false);
        ReflectionTestUtils.setField(fixed, "entityManager", em2);
        fixed.begin(new CurrentUser(1L, CurrentUser.Role.REQUESTER));
        fixed.reset();
        verify(em2, times(2)).createNativeQuery(anyString());
        verify(query).setParameter("clientId", "1");
    }
}
