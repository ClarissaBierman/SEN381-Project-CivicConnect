package com.civicconnect.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.mockito.Mockito.*;

/** DbSession is applied automatically, once per transaction, and again only when the answer changes. */
class DbSessionInterceptorTest {

    /** Stands in for a @Service. */
    public static class FakeService {
        public void work() {}
        @AppRoleOnly
        public void appRoleWork() {}
    }

    private final DbSession db = mock(DbSession.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final FakeService service;

    @SuppressWarnings("unchecked")
    DbSessionInterceptorTest() {
        ObjectProvider<DbSession> dbProvider = mock(ObjectProvider.class);
        when(dbProvider.getObject()).thenReturn(db);
        ObjectProvider<CurrentUserService> userProvider = mock(ObjectProvider.class);
        when(userProvider.getObject()).thenReturn(currentUser);
        ProxyFactory factory = new ProxyFactory(new FakeService());
        factory.addAdvice(new DbSessionInterceptor(dbProvider, userProvider));
        factory.setProxyTargetClass(true);
        service = (FakeService) factory.getProxy();
    }

    private void inTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void loggedInAs(CurrentUser user) {
        when(currentUser.find()).thenReturn(Optional.ofNullable(user));
    }

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.unbindResourceIfPossible(DbSessionInterceptor.STATE_KEY);
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("Inside a transaction, the logged-in user's role is set once, not on every nested call")
    void setsRoleOncePerTransaction() {
        inTransaction();
        loggedInAs(staff(3));

        service.work();
        service.work();

        verify(db, times(1)).begin(staff(3));
        verify(db, never()).reset();
    }

    @Test
    @DisplayName("If the user changes within the transaction, the role is set again")
    void userChanges() {
        inTransaction();
        loggedInAs(requester(1));
        service.work();
        loggedInAs(manager(1));
        service.work();

        verify(db).begin(requester(1));
        verify(db).begin(manager(1));
    }

    @Test
    @DisplayName("@AppRoleOnly goes back to the application role after a user role was set; nothing is done if none was")
    void appRoleOnly() {
        inTransaction();
        loggedInAs(staff(3));

        service.appRoleWork();
        verifyNoInteractions(db);

        service.work();
        service.appRoleWork();
        verify(db).begin(staff(3));
        verify(db).reset();
    }

    @Test
    @DisplayName("Nobody logged in (login, registration): the application role is kept")
    void noUser() {
        inTransaction();
        loggedInAs(null);

        service.work();

        verifyNoInteractions(db);
    }

    @Test
    @DisplayName("Outside a transaction there is no database work to scope, so nothing is set")
    void outsideTransaction() {
        loggedInAs(staff(3));

        service.work();

        verifyNoInteractions(db);
    }
}
