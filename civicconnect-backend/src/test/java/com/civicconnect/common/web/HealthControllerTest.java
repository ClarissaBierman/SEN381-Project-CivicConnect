package com.civicconnect.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** NFR-005: the health check says UP only when the database answers. */
class HealthControllerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HealthController controller = new HealthController(jdbc);

    @Test
    @DisplayName("Database answers: 200 UP")
    void up() {
        when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        var response = controller.health();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("Database unreachable (e.g. Supabase paused): 503 DOWN, no error details")
    void down() {
        when(jdbc.queryForObject("select 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("connection refused to db.example:5432"));
        var response = controller.health();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        assertThat(response.getBody().toString()).doesNotContain("db.example");
    }
}
