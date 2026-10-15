package com.civicconnect.web;

import com.civicconnect.access.controller.AuthController;
import com.civicconnect.access.controller.CategoryHandlersController;
import com.civicconnect.access.controller.ClientController;
import com.civicconnect.access.controller.EmployeeController;
import com.civicconnect.access.controller.HandlerCategoryController;
import com.civicconnect.access.service.AuthService;
import com.civicconnect.access.service.ClientService;
import com.civicconnect.access.service.EmployeeService;
import com.civicconnect.common.error.GlobalExceptionHandler;
import com.civicconnect.reporting.controller.AuditController;
import com.civicconnect.reporting.controller.ReportController;
import com.civicconnect.reporting.domain.AuditResource;
import com.civicconnect.reporting.service.AuditService;
import com.civicconnect.reporting.service.ReportService;
import com.civicconnect.requests.controller.CategoryController;
import com.civicconnect.requests.controller.CommentController;
import com.civicconnect.requests.controller.RequestController;
import com.civicconnect.requests.controller.RequestHandlerController;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.service.CategoryService;
import com.civicconnect.requests.service.CommentService;
import com.civicconnect.requests.service.RequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each endpoint maps to the right service call with the documented status code. */
class ControllerRoutingTest {

    private final AuthService auth = mock(AuthService.class);
    private final ClientService clients = mock(ClientService.class);
    private final EmployeeService employees = mock(EmployeeService.class);
    private final CategoryService categories = mock(CategoryService.class);
    private final CommentService comments = mock(CommentService.class);
    private final RequestService requests = mock(RequestService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ReportService reports = mock(ReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(auth), new ClientController(clients), new EmployeeController(employees),
                        new HandlerCategoryController(employees), new CategoryHandlersController(employees),
                        new CategoryController(categories), new CommentController(comments),
                        new RequestController(requests), new RequestHandlerController(requests),
                        new AuditController(audit), new ReportController(reports))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        when(employees.list(any())).thenReturn(Page.empty());
        when(requests.mine(any())).thenReturn(Page.empty());
        when(audit.search(any(), any(), any(), any())).thenReturn(Page.empty());
    }

    private void expect(MockHttpServletRequestBuilder request, int status) throws Exception {
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().is(status));
    }

    @Test
    @DisplayName("Access endpoints")
    void accessEndpoints() throws Exception {
        expect(post("/api/v1/auth/register").content(
                "{\"firstName\":\"A\",\"surname\":\"B\",\"email\":\"a@b.co\",\"password\":\"Secret123!\"}"), 201);
        expect(post("/api/v1/auth/login").content("{\"email\":\"a@b.co\",\"password\":\"x\"}"), 200);
        expect(post("/api/v1/auth/register").content("{\"firstName\":\"A\",\"surname\":\"B\",\"email\":\"a@b.co\",\"password\":\""
                + "é".repeat(60) + "\"}"), 400);
        expect(post("/api/v1/auth/login").content("{\"email\":\"a@b.co\",\"password\":\"" + "é".repeat(60) + "\"}"), 400);
        expect(get("/api/v1/auth/me"), 200);
        expect(get("/api/v1/clients/me"), 200);
        expect(patch("/api/v1/clients/me").content("{\"firstName\":\"A\"}"), 200);
        expect(get("/api/v1/clients/6/contact"), 200);
        expect(patch("/api/v1/clients/6/active").content("{\"active\":false}"), 204);
        expect(get("/api/v1/employees?sort=surname"), 200);
        expect(get("/api/v1/employees?sort=password"), 400);
        expect(get("/api/v1/employees/3"), 200);
        expect(post("/api/v1/employees").content("{\"firstName\":\"A\",\"surname\":\"B\",\"email\":\"a@b.co\","
                + "\"password\":\"Secret123!\",\"role\":\"STAFF\"}"), 201);
        expect(patch("/api/v1/employees/3").content("{\"active\":false}"), 200);
        expect(get("/api/v1/employees/3/categories"), 200);
        expect(put("/api/v1/employees/3/categories").content("{\"categoryIds\":[2,4]}"), 200);
        expect(get("/api/v1/categories/4/handlers"), 200);

        verify(clients).setActive(6L, false);
        verify(employees).handlersFor(4L);
    }

    @Test
    @DisplayName("Requests, categories, comments, audit and reports endpoints")
    void requestEndpoints() throws Exception {
        expect(get("/api/v1/categories?active=true"), 200);
        expect(get("/api/v1/categories/4"), 200);
        expect(post("/api/v1/categories").content("{\"catName\":\"Roads\"}"), 201);
        expect(patch("/api/v1/categories/4").content("{\"active\":false}"), 200);
        expect(get("/api/v1/requests/10"), 200);
        expect(get("/api/v1/requests/mine"), 200);
        expect(post("/api/v1/requests/10/assignment"), 200);
        expect(patch("/api/v1/requests/10/priority").content("{\"priority\":\"HIGH\"}"), 200);
        expect(patch("/api/v1/requests/10/priority").content("{}"), 400);
        expect(post("/api/v1/requests/10/comments").content("{\"content\":\"Note\"}"), 201);
        expect(get("/api/v1/requests/10/comments"), 200);
        expect(get("/api/v1/requests/10/audit"), 200);
        expect(get("/api/v1/audit/clients?recordId=6&operation=UPDATE"), 200);
        expect(get("/api/v1/audit/clients/31"), 200);
        expect(get("/api/v1/audit/nonsense"), 400);
        expect(get("/api/v1/reports/activity?from=2026-10-01&to=2026-10-31&categoryId=4&status=NEW"), 200);

        verify(categories).list(true);
        verify(requests).setPriority(10L, ServiceRequest.Priority.HIGH);
        verify(audit).search(eq(AuditResource.CLIENTS), eq("6"), eq("UPDATE"), any());
        verify(audit).get(AuditResource.CLIENTS, 31L);
        verify(reports).activity(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 4L, ServiceRequest.Status.NEW);
    }
}
