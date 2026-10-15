package com.civicconnect.common.error;

import com.civicconnect.requests.dto.StatusChangeRequest;
import com.civicconnect.requests.controller.RequestController;
import com.civicconnect.requests.service.RequestService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The PED error model over real HTTP: every error has status, code, detail, instance and fieldErrors,
 * and the Angular app's problemMessage() can switch on the code.
 * Uses the real RequestController and GlobalExceptionHandler with a mocked service.
 */
class ApiErrorFormatTest {

    private RequestService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RequestService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RequestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("Invalid form input returns 400 VALIDATION_FAILED with one entry per field")
    void validationErrors() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\",\"location\":\"Hall\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("categoryId", "description")))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests"))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Attachment links must be http or https")
    void attachmentUrlValidated() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"description\":\"d\",\"location\":\"l\",\"attachmentUrl\":\"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("attachmentUrl"));
    }

    @Test
    @DisplayName("A business rule error keeps its documented status, code and message")
    void businessRuleError() throws Exception {
        when(service.changeStatus(eq(13L), any(StatusChangeRequest.class)))
                .thenThrow(new ApiException(ErrorCode.REQUEST_CLOSED, "Closed or rejected requests are read-only."));

        mvc.perform(patch("/api/v1/requests/13/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("REQUEST_CLOSED"))
                .andExpect(jsonPath("$.detail").value("Closed or rejected requests are read-only."));
    }

    @Test
    @DisplayName("Broken JSON returns 400, not a server error")
    void malformedJson() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("An unknown status in a filter returns 400")
    void badEnumParameter() throws Exception {
        mvc.perform(get("/api/v1/requests").param("status", "FINISHED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("A version clash returns 409 VERSION_CONFLICT (DEC-006)")
    void optimisticLockConflict() throws Exception {
        when(service.get(13L)).thenThrow(new ObjectOptimisticLockingFailureException("ServiceRequest", 13L));

        mvc.perform(get("/api/v1/requests/13"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("The database's one-active-handler rule returns 409 ALREADY_ASSIGNED")
    void secondActiveHandler() throws Exception {
        when(service.get(13L)).thenThrow(new DataIntegrityViolationException("duplicate",
                new RuntimeException("violates unique constraint \"ux_request_handler_active\"")));

        mvc.perform(get("/api/v1/requests/13"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"));
    }

    @Test
    @DisplayName("A database that cannot be reached returns 503 (NFR-005, paused Supabase project)")
    void databaseDown() throws Exception {
        when(service.get(13L)).thenThrow(new CannotCreateTransactionException("Connection refused"));

        mvc.perform(get("/api/v1/requests/13"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Unexpected errors return 500 with a generic message - no internal details leak")
    void unexpectedError() throws Exception {
        when(service.get(13L)).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/api/v1/requests/13"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @Test
    @DisplayName("?sort= on a field that does not exist, or a nested one, is 400 on the sort field - never 500")
    void badSortIs400() throws Exception {
        for (String sort : new String[] {"foo", "requester.password", "createdAt,desc&sort=description"}) {
            mvc.perform(get("/api/v1/requests?sort=" + sort))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("A sort the endpoint allows reaches the service")
    void allowedSortPasses() throws Exception {
        when(service.list(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        mvc.perform(get("/api/v1/requests?sort=priority,desc")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Safety net: a PropertyReferenceException from any paged query is 400, not 500")
    void propertyReferenceIs400() throws Exception {
        when(service.list(any(), any())).thenThrow(new org.springframework.data.core.PropertyReferenceException(
                "nope", org.springframework.data.core.TypeInformation.of(com.civicconnect.requests.domain.ServiceRequest.class),
                java.util.List.of()));

        mvc.perform(get("/api/v1/requests"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }
}
