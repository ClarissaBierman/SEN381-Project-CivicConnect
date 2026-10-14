package com.civicconnect.integration;

import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API tests over HTTP through the whole stack: security filter chain (JWT, account check, rate limit),
 * @PreAuthorize role rules, controllers, services, database roles, audit trigger and migrations V3/V4.
 * Uses the dummy data from database/civicconnect_supabase_build.sql:
 *   clients 1 Thabo, 2 Naledi, 6 Fatima (deactivated); employees 1 Lerato (manager),
 *   2 Johan (staff: Facility Faults 1, Maintenance 5, Other 7), 3 Ayesha (staff: 2, 4), 5 Megan (deactivated).
 *
 * Every test runs in one transaction that is rolled back, so nothing is left behind.
 * Tagged "integration": run with  .\mvnw test -Pintegration
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class ApiHttpIntegrationTest {

    private static final long THABO = 1, NALEDI = 2, FATIMA = 6;
    private static final long LERATO = 1, JOHAN = 2, AYESHA = 3, MEGAN = 5;
    private static final long FACILITY_FAULTS = 1;

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private String token(long id, CurrentUser.Role role) {
        return jwt.issue(new CurrentUser(id, role)).token();
    }

    private String requester(long id) { return token(id, CurrentUser.Role.REQUESTER); }
    private String staff(long id) { return token(id, CurrentUser.Role.STAFF); }
    private String manager(long id) { return token(id, CurrentUser.Role.MANAGEMENT); }

    /**
     * Sends a request with the token. In production each HTTP request is its own transaction; here they
     * share the test transaction, so the database role DbSession switched to for the previous call is
     * reset first - exactly what a new transaction would do.
     */
    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        em.flush(); // write the previous call's changes under its own role, as its commit would
        jdbc.execute("RESET ROLE");
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
    }

    private long submitRequest(long clientId, long categoryId) throws Exception {
        MvcResult result = call(post("/api/v1/requests").content(
                "{\"categoryId\":" + categoryId + ",\"description\":\"HTTP test\",\"location\":\"Hall\"}"), requester(clientId))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    // ------------------------------------------------------------------ roles (NFR-003, ASR-001)

    @Test
    @DisplayName("A requester's valid token on a staff endpoint is 403, and no data is returned")
    void requesterCannotUseStaffEndpoints() throws Exception {
        call(patch("/api/v1/requests/1/status").content("{\"toStatus\":\"CLOSED\"}"), requester(THABO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        call(get("/api/v1/requests"), requester(THABO)).andExpect(status().isForbidden());
        call(get("/api/v1/reports/activity"), staff(AYESHA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No token is 401 with the error-model body")
    void noTokenIs401() throws Exception {
        call(get("/api/v1/requests/mine"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ------------------------------------------------------------------ B1: deactivated accounts

    @Test
    @DisplayName("A deactivated employee's unexpired token is refused (401 'no longer active')")
    void deactivatedEmployeeTokenRefused() throws Exception {
        call(get("/api/v1/requests"), staff(MEGAN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("no longer active")));
    }

    @Test
    @DisplayName("Deactivating a staff member cuts off their open session at once, and releases their requests")
    void deactivationTakesEffectImmediately() throws Exception {
        long id = submitRequest(THABO, FACILITY_FAULTS);
        String johan = staff(JOHAN);
        call(post("/api/v1/requests/" + id + "/assignment"), johan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.handlerName").value("Johan van Wyk"));

        call(patch("/api/v1/employees/" + JOHAN).content("{\"active\":false}"), manager(LERATO))
                .andExpect(status().isOk());

        call(get("/api/v1/requests"), johan).andExpect(status().isUnauthorized());
        call(get("/api/v1/requests/" + id), manager(LERATO))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.handlerId").value(nullValue()))
                .andExpect(jsonPath("$.comments[0].content").value(containsString("returned to the queue")))
                .andExpect(jsonPath("$.comments[0].visibleToRequester").value(false));
    }

    @Test
    @DisplayName("The only active manager cannot deactivate themselves (422 LAST_MANAGER)")
    void lastManagerCannotBeDeactivated() throws Exception {
        call(patch("/api/v1/employees/" + LERATO).content("{\"active\":false}"), manager(LERATO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAST_MANAGER"));
    }

    // ------------------------------------------------------------------ client data (ASR-005)

    @Test
    @DisplayName("Management can look up one client's contact details, but the client audit never shows them")
    void clientContactVersusAudit() throws Exception {
        call(get("/api/v1/clients/" + FATIMA + "/contact"), manager(LERATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("fatima.adams@example.com"))
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist());

        call(get("/api/v1/audit/clients?recordId=" + FATIMA), manager(LERATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resource").value("clients"))
                .andExpect(jsonPath("$.content[0].newData.client_id").value(FATIMA))
                .andExpect(content().string(not(containsString("fatima"))))
                .andExpect(content().string(not(containsString("0820000006"))));

        call(get("/api/v1/clients/" + FATIMA + "/contact"), staff(AYESHA)).andExpect(status().isForbidden());
        call(get("/api/v1/audit/nonsense"), manager(LERATO)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ priority, history, reports, UTC

    @Test
    @DisplayName("A manager sets the priority (API-12); staff cannot; the history shows who set it")
    void priorityAndHistory() throws Exception {
        long id = submitRequest(THABO, 4);

        call(patch("/api/v1/requests/" + id + "/priority").content("{\"priority\":\"HIGH\"}"), staff(AYESHA))
                .andExpect(status().isForbidden());
        call(patch("/api/v1/requests/" + id + "/priority").content("{\"priority\":\"HIGH\"}"), manager(LERATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").value(endsWith("Z")));

        call(get("/api/v1/requests/" + id + "/audit"), staff(AYESHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("submitted the request"))
                .andExpect(jsonPath("$[0].actor").value("Requester #" + THABO))
                .andExpect(jsonPath("$[1].action").value("set the priority to High"))
                .andExpect(jsonPath("$[1].actor").value("Lerato Nkosi"));
    }

    @Test
    @DisplayName("Staff lists show priority and handler names (the V3 grant lets staff read names only)")
    void staffListShowsHandlerNames() throws Exception {
        long id = submitRequest(THABO, 4);
        call(post("/api/v1/requests/" + id + "/assignment"), staff(AYESHA)).andExpect(status().isOk());

        call(get("/api/v1/requests?status=ASSIGNED&categoryId=4&sort=createdAt,desc"), staff(AYESHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].handlerName").value("Ayesha Patel"));
    }

    @Test
    @DisplayName("The activity report (API-17) returns counts only")
    void activityReport() throws Exception {
        call(get("/api/v1/reports/activity?from=2020-01-01"), manager(LERATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(12))
                .andExpect(jsonPath("$.byCategory[0].categoryName").exists())
                .andExpect(content().string(not(containsString("description"))));
    }

    @Test
    @DisplayName("?sort=requester.password is a 400, not a 500 or an ORDER BY password")
    void sortByNestedFieldRefused() throws Exception {
        call(get("/api/v1/requests?sort=requester.password"), manager(LERATO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    // ------------------------------------------------------------------ B4: rate limits

    @Test
    @DisplayName("After 5 failed logins for one email, the 6th attempt is 429 with Retry-After")
    void loginLockoutOverHttp() throws Exception {
        String body = "{\"email\":\"lockout-test@example.com\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 5; i++) {
            call(post("/api/v1/auth/login").content(body), null).andExpect(status().isUnauthorized());
        }
        call(post("/api/v1/auth/login").content(body), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("One account gets a burst of 120 calls, then is refused with 429 and Retry-After; other accounts are not")
    void perAccountApiLimit() throws Exception {
        String naledi = requester(NALEDI);
        int allowed = 0;
        MvcResult refused = null;
        for (int i = 0; i < 400 && refused == null; i++) {
            MvcResult r = call(get("/api/v1/categories"), naledi).andReturn();
            if (r.getResponse().getStatus() == 429) refused = r; else allowed++;
        }
        // At least the burst of 120 got through (a few more may, as tokens refill at 2 per second)
        assertThat(allowed).isBetween(120, 140);
        assertThat(refused).as("eventually refused").isNotNull();
        assertThat(refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(refused.getResponse().getContentAsString()).contains("TOO_MANY_REQUESTS");

        call(get("/api/v1/categories"), requester(THABO)).andExpect(status().isOk());
    }
}
