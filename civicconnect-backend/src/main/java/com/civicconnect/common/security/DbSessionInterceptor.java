package com.civicconnect.common.security;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Runs DbSession automatically, so no service method can forget it.
 *
 * Wraps every public method of every @Service, inside the method's transaction (DbSessionConfig puts
 * it after the transaction interceptor). On each call it works out who the database should act as:
 *  - an @AppRoleOnly method, or no logged-in user: the application's login role, no actor;
 *  - otherwise: the logged-in user's role, with them as the actor for the audit trigger.
 * It remembers what it set for the current transaction, so nested service calls (e.g. RequestService
 * calling StaffDirectory) do not repeat the database round trip; it only acts again when the answer
 * changes. Outside a transaction it does nothing - there is no database work to scope.
 */
public class DbSessionInterceptor implements MethodInterceptor {

    /** Key of the per-transaction resource holding what was last set. */
    static final Object STATE_KEY = DbSessionInterceptor.class.getName() + ".STATE";
    private static final Object APP_ROLE = "app-role";

    private final ObjectProvider<DbSession> db;
    private final ObjectProvider<CurrentUserService> currentUser;

    public DbSessionInterceptor(ObjectProvider<DbSession> db, ObjectProvider<CurrentUserService> currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            apply(wanted(invocation));
        }
        return invocation.proceed();
    }

    private Object wanted(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        if (invocation.getThis() != null) {
            method = AopUtils.getMostSpecificMethod(method, invocation.getThis().getClass());
        }
        if (AnnotatedElementUtils.hasAnnotation(method, AppRoleOnly.class)) {
            return APP_ROLE;
        }
        return currentUser.getObject().find().<Object>map(u -> u).orElse(APP_ROLE);
    }

    private void apply(Object wanted) {
        Object current = TransactionSynchronizationManager.getResource(STATE_KEY);
        // A new transaction starts as the application's login role
        Object effective = current == null ? APP_ROLE : current;
        if (Objects.equals(effective, wanted)) {
            return;
        }
        if (wanted instanceof CurrentUser user) {
            db.getObject().begin(user);
        } else {
            db.getObject().reset();
        }
        if (current == null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(STATE_KEY);
                }
            });
        } else {
            TransactionSynchronizationManager.unbindResource(STATE_KEY);
        }
        TransactionSynchronizationManager.bindResource(STATE_KEY, wanted);
    }
}
