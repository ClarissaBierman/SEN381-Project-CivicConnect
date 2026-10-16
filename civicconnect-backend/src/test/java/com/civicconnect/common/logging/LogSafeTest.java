package com.civicconnect.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-37 (CodeQL log injection): request values are logged on one line and at a bounded length. */
class LogSafeTest {

    @Test
    @DisplayName("Line breaks and control characters cannot start a fake log line")
    void controlCharactersReplaced() {
        assertThat(LogSafe.of("/api/v1/x\r\n2026-10-16 INFO Admin logged in")).doesNotContain("\r", "\n")
                .isEqualTo("/api/v1/x__2026-10-16 INFO Admin logged in");
        assertThat(LogSafe.of("a\tb\u0000c d")).isEqualTo("a_b_c_d");
        assertThat(LogSafe.of("/api/v1/requests?page=2")).isEqualTo("/api/v1/requests?page=2");
        assertThat(LogSafe.of(null)).isEqualTo("null");
    }

    @Test
    @DisplayName("Very long values are cut short")
    void longValuesCut() {
        String logged = LogSafe.of("x".repeat(5000));
        assertThat(logged).hasSize(LogSafe.MAX_LENGTH + 3).endsWith("...");
    }
}
