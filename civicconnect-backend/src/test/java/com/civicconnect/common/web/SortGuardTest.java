package com.civicconnect.common.web;

import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static com.civicconnect.support.TestData.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;

/** ?sort= only accepts the fields an endpoint names (equivalence classes: allowed, unknown, nested). */
class SortGuardTest {

    @Test
    @DisplayName("An allowed field passes through unchanged")
    void allowedField() {
        PageRequest page = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        assertThat(SortGuard.allow(page, "createdAt", "status")).isSameAs(page);
    }

    @Test
    @DisplayName("An unknown field is a 400 on the sort field, listing what is allowed")
    void unknownField() {
        ApiException ex = assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> SortGuard.allow(PageRequest.of(0, 20, Sort.by("foo")), "createdAt", "status"));
        assertThat(ex.getFieldErrors()).singleElement().satisfies(f -> {
            assertThat(f.field()).isEqualTo("sort");
            assertThat(f.message()).contains("'foo'", "createdAt, status");
        });
    }

    @Test
    @DisplayName("A nested field such as requester.password is refused before it reaches the database")
    void nestedFieldRefused() {
        assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> SortGuard.allow(PageRequest.of(0, 20, Sort.by("requester.password")), "createdAt"));
    }
}
