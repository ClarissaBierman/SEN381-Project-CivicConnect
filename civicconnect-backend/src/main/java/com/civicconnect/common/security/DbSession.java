package com.civicconnect.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tells PostgreSQL who is acting in the current transaction. Called automatically by
 * DbSessionInterceptor at the start of every service call - services never call it themselves.
 *
 * 1. Sets civicconnect.emp_id / civicconnect.client_id, which the audit trigger reads to fill the
 *    actor columns of audit_entry (A1, DEC-007).
 * 2. Switches to the database role for the user's type (civicconnect_client / _staff / _management),
 *    so the database itself also limits what each user type can read and change.
 *
 * Both settings are transaction-local (the "true" argument), so they reset at commit or rollback and
 * can never leak to the next user of the same pooled connection.
 */
@Component
public class DbSession {

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean switchRoles;

    public DbSession(@Value("${civicconnect.db.switch-roles:true}") boolean switchRoles) {
        this.switchRoles = switchRoles;
    }

    public void begin(CurrentUser user) {
        entityManager.createNativeQuery(
                        "select set_config('civicconnect.emp_id', :empId, true), "
                                + "set_config('civicconnect.client_id', :clientId, true)")
                .setParameter("empId", user.isEmployee() ? String.valueOf(user.id()) : "")
                .setParameter("clientId", user.isRequester() ? String.valueOf(user.id()) : "")
                .getSingleResult();

        if (switchRoles) {
            entityManager.createNativeQuery("select set_config('role', :role, true)")
                    .setParameter("role", databaseRole(user.role()))
                    .getSingleResult();
        }
    }

    /** Back to the application's login role, with no actor - for @AppRoleOnly methods. */
    public void reset() {
        entityManager.createNativeQuery(
                        "select set_config('civicconnect.emp_id', '', true), set_config('civicconnect.client_id', '', true)")
                .getSingleResult();
        if (switchRoles) {
            entityManager.createNativeQuery("select set_config('role', 'none', true)").getSingleResult();
        }
    }

    static String databaseRole(CurrentUser.Role role) {
        return switch (role) {
            case REQUESTER -> "civicconnect_client";
            case STAFF -> "civicconnect_staff";
            case MANAGEMENT -> "civicconnect_management";
        };
    }
}
