package com.civicconnect.integration;

import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.JwtService;
import com.civicconnect.common.time.Time;
import com.civicconnect.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Black-box API tests (M3): each test is designed from the API contract and acceptance criteria only -
 * boundary value analysis (BVA) and equivalence partitioning (EP) of the inputs - and checks the HTTP
 * status, the error code of the error model and the data, not how the code is written.
 * Dummy data from database/civicconnect_supabase_build.sql: client 1 Thabo; employees 1 Lerato (manager),
 * 3 Ayesha (staff: Damaged Equipment 2, IT Support 4); category 8 Parking is inactive.
 *
 * The per-IP limit is raised for this class only: every MockMvc call comes from 127.0.0.1, and rate limiting
 * is tested elsewhere (ApiHttpIntegrationTest, RateLimitOrderIntegrationTest).
 * Every test runs in one transaction that is rolled back. Tagged "integration": run with .\mvnw test -Pintegration
 */
@SpringBootTest(properties = "civicconnect.rate-limit.ip.requests-per-minute=100000")
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class ApiBlackBoxIntegrationTest {

    private static final long THABO = 1;
    private static final long LERATO = 1, AYESHA = 3;
    private static final long IT_SUPPORT = 4, PARKING_INACTIVE = 8;

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    @AfterEach
    void systemClock() {
        Time.useClock(null);
    }

    private String requester(long id) { return jwt.issue(new CurrentUser(id, CurrentUser.Role.REQUESTER)).token(); }
    private String staff(long id) { return jwt.issue(new CurrentUser(id, CurrentUser.Role.STAFF)).token(); }
    private String manager(long id) { return jwt.issue(new CurrentUser(id, CurrentUser.Role.MANAGEMENT)).token(); }

    /** Same as ApiHttpIntegrationTest.call: reset the database role the previous call switched to. */
    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        em.flush();
        jdbc.execute("RESET ROLE");
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
    }

    private static String newRequest(Object categoryId, String description, String location) {
        return "{\"categoryId\":" + categoryId + ",\"description\":\"" + description + "\",\"location\":\"" + location + "\"}";
    }

    private long submit(long categoryId) throws Exception {
        MvcResult result = call(post("/api/v1/requests").content(newRequest(categoryId, "Black-box test", "Hall")),
                requester(THABO)).andExpect(status().isCreated()).andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
    }

    // ------------------------------------------------------------------ BVA: field lengths (FR-001, API-04)

    @ParameterizedTest(name = "description of {0} characters -> {1}")
    @CsvSource({"0, 400", "1, 201", "2000, 201", "2001, 400"})
    @DisplayName("BVA description: empty and 2001 characters refused, 1 and 2000 accepted")
    void descriptionBoundaries(int length, int expected) throws Exception {
        ResultActions result = call(post("/api/v1/requests").content(newRequest(IT_SUPPORT, "d".repeat(length), "Hall")),
                requester(THABO)).andExpect(status().is(expected));
        if (expected == 400) {
            result.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
        } else {
            result.andExpect(jsonPath("$.status").value("NEW"));
        }
    }

    @ParameterizedTest(name = "location of {0} characters -> {1}")
    @CsvSource({"255, 201", "256, 400"})
    @DisplayName("BVA location: 255 characters accepted, 256 refused")
    void locationBoundaries(int length, int expected) throws Exception {
        call(post("/api/v1/requests").content(newRequest(IT_SUPPORT, "Leaking tap", "l".repeat(length))), requester(THABO))
                .andExpect(status().is(expected));
    }

    // ------------------------------------------------------------------ EP: category on submission (FR-015)

    @Test
    @DisplayName("EP category: active accepted; inactive 422 CATEGORY_INACTIVE; unknown and missing 400 on categoryId")
    void categoryPartitions() throws Exception {
        String thabo = requester(THABO);
        call(post("/api/v1/requests").content(newRequest(IT_SUPPORT, "Printer offline", "Office")), thabo)
                .andExpect(status().isCreated());
        call(post("/api/v1/requests").content(newRequest(PARKING_INACTIVE, "Lines faded", "Car park")), thabo)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_INACTIVE"));
        call(post("/api/v1/requests").content(newRequest(999_999, "No such category", "Office")), thabo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
        call(post("/api/v1/requests").content(newRequest(null, "Category missing", "Office")), thabo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
    }

    // ------------------------------------------------------------------ EP + state: status change body (FR-009, API-09)

    @Test
    @DisplayName("EP status change: unknown value 400, missing 400, illegal move 422, legal move 200, then read-only 422")
    void statusChangePartitions() throws Exception {
        long id = submit(IT_SUPPORT);
        String ayesha = staff(AYESHA); // in scope for IT Support, not the handler: may only reject a New request

        call(patch("/api/v1/requests/" + id + "/status").content("{\"toStatus\":\"DONE\"}"), ayesha)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        call(patch("/api/v1/requests/" + id + "/status").content("{\"note\":\"no status\"}"), ayesha)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("toStatus"));
        call(patch("/api/v1/requests/" + id + "/status").content("{\"toStatus\":\"CLOSED\"}"), ayesha)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        call(patch("/api/v1/requests/" + id + "/status").content("{\"toStatus\":\"REJECTED\",\"note\":\"Duplicate\"}"), ayesha)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        call(patch("/api/v1/requests/" + id + "/status").content("{\"toStatus\":\"IN_PROGRESS\"}"), manager(LERATO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUEST_CLOSED"));
    }

    @Test
    @DisplayName("EP priority (API-12): Low/Medium/High accepted, any other value or none refused with 400")
    void priorityPartitions() throws Exception {
        long id = submit(IT_SUPPORT);
        String lerato = manager(LERATO);
        for (String valid : new String[] {"LOW", "MEDIUM", "HIGH"}) {
            call(patch("/api/v1/requests/" + id + "/priority").content("{\"priority\":\"" + valid + "\"}"), lerato)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priority").value(valid));
        }
        call(patch("/api/v1/requests/" + id + "/priority").content("{\"priority\":\"URGENT\"}"), lerato)
                .andExpect(status().isBadRequest());
        call(patch("/api/v1/requests/" + id + "/priority").content("{}"), lerato)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priority"));
    }

    // ------------------------------------------------------------------ BVA: overdue after 7 days (FR-012, CR-004)

    @Test
    @DisplayName("BVA overdue (CR-004): open for 7 days minus 1 s and exactly 7 days is not overdue; 7 days plus 1 s is")
    void overdueBoundary() throws Exception {
        // A day with no other requests, so the report counts only this one
        Instant submitted = Instant.parse("2030-03-01T09:00:00Z");
        MutableClock clock = new MutableClock(submitted);
        Time.useClock(clock);
        submit(IT_SUPPORT);
        String report = "/api/v1/reports/activity?from=2030-03-01&to=2030-03-01";
        String lerato = manager(LERATO);

        clock.advance(Duration.ofDays(7).minusSeconds(1));
        String before = call(get(report), lerato).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(before, "$.totals.total")).as("precondition: only this request").isEqualTo(1);
        assertThat(JsonPath.<Integer>read(before, "$.totals.open")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(before, "$.totals.overdue")).isZero();

        clock.advance(Duration.ofSeconds(1));
        call(get(report), lerato).andExpect(jsonPath("$.totals.overdue").value(0));

        clock.advance(Duration.ofSeconds(1));
        call(get(report), lerato).andExpect(jsonPath("$.totals.overdue").value(1));
    }

    // ------------------------------------------------------------------ BVA: page size (NFR-009)

    @ParameterizedTest(name = "size={0} -> page size {1}")
    @CsvSource({"1, 1", "100, 100", "101, 100", "5000, 100"})
    @DisplayName("BVA page size: up to 100 rows per page as asked, anything larger is capped at 100")
    void pageSizeCap(int asked, int expected) throws Exception {
        call(get("/api/v1/requests?size=" + asked), manager(LERATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(expected));
    }

    // ------------------------------------------------------------------ negative: malformed and unknown input

    @Test
    @DisplayName("Negative: malformed JSON is a 400 in the error model (no stack trace); an unknown request ID is 404")
    void malformedAndUnknown() throws Exception {
        call(post("/api/v1/requests").content("{\"categoryId\": 4, \"description\": "), requester(THABO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("Exception"))));
        call(get("/api/v1/requests/999999"), manager(LERATO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
