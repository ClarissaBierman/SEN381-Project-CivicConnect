package com.civicconnect.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    static String databaseRole(CurrentUser.Role role) {
        return switch (role) {
            case REQUESTER -> "civicconnect_client";
            case STAFF -> "civicconnect_staff";
            case MANAGEMENT -> "civicconnect_management";
        };
    }
}
