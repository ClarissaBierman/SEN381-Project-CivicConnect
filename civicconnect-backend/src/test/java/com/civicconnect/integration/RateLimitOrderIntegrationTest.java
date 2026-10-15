package com.civicconnect.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-24 (B4): the per-IP limit runs before the token is checked, so calls with a forged or expired token
 * are counted too. Before the fix they were refused with 401 before the limiter, so one machine could
 * send them without limit. Uses a tiny per-IP limit for this test only.
 */
@SpringBootTest(properties = "civicconnect.rate-limit.ip.requests-per-minute=3")
@AutoConfigureMockMvc
@Tag("integration")
class RateLimitOrderIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("Bad tokens count against the IP: 401, 401, 401, then 429 with Retry-After")
    void badTokensAreRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/requests/mine").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value")
                            .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/requests/mine").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value")
                        .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
