package com.civicconnect;

import com.civicconnect.access.dto.AuthResponse;
import com.civicconnect.access.dto.ClientResponse;
import com.civicconnect.access.dto.EmployeeResponse;
import com.civicconnect.access.dto.MeResponse;
import com.civicconnect.reporting.dto.AuditEntryResponse;
import com.civicconnect.requests.dto.CategoryResponse;
import com.civicconnect.requests.dto.CommentResponse;
import com.civicconnect.requests.dto.RequestDetail;
import com.civicconnect.requests.dto.RequestSummary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ASR-005 / NFR-002: nothing the API sends back can carry a password or password hash. */
class ResponsesNeverContainPasswordsTest {

    @Test
    @DisplayName("No response DTO has a password field")
    void noPasswordFields() {
        List<Class<? extends Record>> responses = List.of(ClientResponse.class, EmployeeResponse.class,
                MeResponse.class, AuthResponse.class, RequestDetail.class, RequestSummary.class,
                CommentResponse.class, CategoryResponse.class, AuditEntryResponse.class);

        for (Class<? extends Record> dto : responses) {
            List<String> fields = Arrays.stream(dto.getRecordComponents()).map(RecordComponent::getName).toList();
            assertThat(fields).as(dto.getSimpleName()).noneMatch(f -> f.toLowerCase().contains("password"));
        }
    }
}
