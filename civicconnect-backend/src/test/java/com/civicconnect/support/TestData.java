package com.civicconnect.support;

import com.civicconnect.access.domain.Client;
import com.civicconnect.access.domain.Employee;
import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.domain.ServiceRequest;

import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Small builders for test objects, so each test only shows the data that matters to it. */
public final class TestData {

    private TestData() {}

    public static CurrentUser requester(long id) { return new CurrentUser(id, CurrentUser.Role.REQUESTER); }

    public static CurrentUser staff(long id) { return new CurrentUser(id, CurrentUser.Role.STAFF); }

    public static CurrentUser manager(long id) { return new CurrentUser(id, CurrentUser.Role.MANAGEMENT); }

    public static Category category(long id, String name, boolean active) {
        Category c = new Category();
        c.setCatId(id);
        c.setCatName(name);
        c.setActive(active);
        return c;
    }

    public static Client client(long id) {
        Client c = new Client();
        c.setClientId(id);
        c.setFirstName("Test");
        c.setSurname("Client");
        c.setEmail("client" + id + "@example.com");
        return c;
    }

    public static Employee employee(long id, Employee.Role role, boolean active) {
        Employee e = new Employee();
        e.setEmpId(id);
        e.setFirstName("Test");
        e.setSurname("Employee");
        e.setEmail("employee" + id + "@civicconnect.example");
        e.setRole(role);
        e.setActive(active);
        return e;
    }

    /** A request in the given status and category, submitted by the given client. */
    public static ServiceRequest request(long id, ServiceRequest.Status status, Category category, long requesterId) {
        ServiceRequest r = new ServiceRequest();
        r.setReqId(id);
        r.setRefNum("CC-2026-" + String.format("%06d", id));
        r.setCategory(category);
        r.setRequester(client(requesterId));
        r.setDescription("Test request");
        r.setLocation("Test location");
        r.setStatus(status);
        return r;
    }

    /** Runs the code and checks it fails with the given documented error code. */
    public static ApiException assertApiError(ErrorCode expected, Executable code) {
        ApiException ex = assertThrows(ApiException.class, code);
        assertThat(ex.getCode()).isEqualTo(expected);
        return ex;
    }
}
