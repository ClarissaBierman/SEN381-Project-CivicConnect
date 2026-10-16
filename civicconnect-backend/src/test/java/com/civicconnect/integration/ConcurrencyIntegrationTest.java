package com.civicconnect.integration;

import com.civicconnect.access.dto.EmployeePatch;
import com.civicconnect.access.service.EmployeeService;
import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.JwtService;
import com.civicconnect.requests.service.RequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Two users at the same moment, in two real database transactions on two connections (the other
 * integration tests share one transaction, so they cannot show this). Checks that the row lock on a
 * request (C-01) makes the second writer wait and then work with the first writer's committed data:
 *  - FR-008: two accepts of the same New request - exactly one wins, the other gets ALREADY_ASSIGNED;
 *  - D-20: removing a handler while a manager changes the priority keeps the manager's change.
 * These tests commit, so each one puts the dummy data it changed back afterwards (the audit trail keeps
 * a record of the test, as it would of any change).
 */
@SpringBootTest
@Tag("integration")
class ConcurrencyIntegrationTest {

    private static final long LERATO = 1, JOHAN = 2;
    private static final long NEW_REQUEST = 11;          // New, category Other (7), Johan covers it
    private static final long JOHANS_IN_PROGRESS = 5;    // In Progress, handled by Johan

    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RequestService requests;
    @Autowired private EmployeeService employees;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopThreads() {
        pool.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    /** Runs code on another thread as a logged-in user (the services read the user from the security context). */
    private <T> CompletableFuture<T> asUser(long id, String role, Supplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(id))
                    .claim(JwtService.ROLE_CLAIM, role).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            try {
                return work.get();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, pool);
    }

    /** SQL as the management database role, committed (to read or restore the dummy data). */
    private <T> T asManagement(java.util.function.Function<JdbcTemplate, T> work) {
        return new TransactionTemplate(txManager).execute(s -> {
            jdbc.queryForList("select set_config('civicconnect.emp_id', '1', true), set_config('role', 'civicconnect_management', true)");
            return work.apply(jdbc);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("other thread reached the point").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("FR-008: two accepts of the same New request at once - the second waits for the lock, then gets ALREADY_ASSIGNED")
    void concurrentAcceptsOneWins() throws Exception {
        Map<String, Object> before = asManagement(j -> j.queryForMap(
                "select status, updated_at from request where req_id = ?", NEW_REQUEST));
        assertThat(before.get("status")).isEqualTo("NEW");
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            CompletableFuture<Object> first = asUser(JOHAN, "STAFF", () -> new TransactionTemplate(txManager).execute(s -> {
                requests.assign(NEW_REQUEST, null);   // locks the request row until this transaction commits
                firstHoldsLock.countDown();
                await(releaseFirst);
                return "assigned";
            }));
            await(firstHoldsLock);

            CompletableFuture<Object> second = asUser(JOHAN, "STAFF", () -> new TransactionTemplate(txManager)
                    .execute(s -> requests.assign(NEW_REQUEST, null)));
            Thread.sleep(500);
            assertThat(second).as("the second accept waits for the first one's row lock").isNotDone();

            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("assigned");
            Throwable lost = catchThrowable(() -> second.get(10, TimeUnit.SECONDS));
            assertThat(lost).hasCauseInstanceOf(ApiException.class);
            assertThat(((ApiException) lost.getCause()).getCode()).isEqualTo(ErrorCode.ALREADY_ASSIGNED);

            Integer activeHandlers = asManagement(j -> j.queryForObject(
                    "select count(*) from request_handler where req_id = ? and unassigned_at is null", Integer.class,
                    NEW_REQUEST));
            assertThat(activeHandlers).as("exactly one active handler").isOne();
        } finally {
            releaseFirst.countDown();
            asManagement(j -> {
                j.update("update request_handler set unassigned_at = now() where req_id = ? and unassigned_at is null",
                        NEW_REQUEST);
                return j.update("update request set status = 'NEW', updated_at = ? where req_id = ?",
                        before.get("updated_at"), NEW_REQUEST);
            });
        }
    }

    @Test
    @DisplayName("D-20: deactivating a handler while a manager sets the priority keeps the manager's priority")
    void handlerRemovalKeepsConcurrentChange() throws Exception {
        Map<String, Object> before = asManagement(j -> j.queryForMap(
                "select status, priority, updated_at from request where req_id = ?", JOHANS_IN_PROGRESS));
        assertThat(before.get("status")).isEqualTo("IN_PROGRESS");
        String newPriority = "HIGH".equals(before.get("priority")) ? "LOW" : "HIGH";
        CountDownLatch managerHoldsLock = new CountDownLatch(1);
        CountDownLatch commitManager = new CountDownLatch(1);
        try {
            // A manager changes the priority and has not committed yet (the request row is locked)
            CompletableFuture<Object> manager = asUser(LERATO, "MANAGEMENT", () -> new TransactionTemplate(txManager).execute(s -> {
                jdbc.queryForList("select set_config('civicconnect.emp_id', '1', true), "
                        + "set_config('role', 'civicconnect_management', true)");
                jdbc.update("update request set priority = ? where req_id = ?", newPriority, JOHANS_IN_PROGRESS);
                managerHoldsLock.countDown();
                await(commitManager);
                return "committed";
            }));
            await(managerHoldsLock);

            // Another manager deactivates Johan, which releases his open requests (rolled back afterwards)
            CompletableFuture<Object> removal = asUser(LERATO, "MANAGEMENT", () -> {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                return tx.execute(s -> {
                    employees.update(JOHAN, new EmployeePatch(null, null, null, null, null, null, false,
                            null, null, null, null, null));
                    Object seen = jdbc.queryForObject("select priority from request where req_id = ?",
                            String.class, JOHANS_IN_PROGRESS);
                    s.setRollbackOnly();
                    return seen;
                });
            });
            Thread.sleep(500);
            assertThat(removal).as("the release waits for the manager's row lock").isNotDone();

            commitManager.countDown();
            assertThat(manager.get(10, TimeUnit.SECONDS)).isEqualTo("committed");
            assertThat(removal.get(10, TimeUnit.SECONDS))
                    .as("the release re-read the request after the lock and kept the new priority")
                    .isEqualTo(newPriority);
        } finally {
            commitManager.countDown();
            asManagement(j -> j.update("update request set priority = ?, updated_at = ? where req_id = ?",
                    before.get("priority"), before.get("updated_at"), JOHANS_IN_PROGRESS));
        }
    }
}
