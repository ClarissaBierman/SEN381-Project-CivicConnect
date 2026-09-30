package com.civicconnect.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "request_handler")
public class RequestHandler {

    @EmbeddedId
    private RequestHandlerId id = new RequestHandlerId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("reqId")
    @JoinColumn(name = "req_id", nullable = false)
    private ServiceRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("empId")
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    public RequestHandler() {}

    public RequestHandlerId getId() { return id; }
    public void setId(RequestHandlerId id) { this.id = id; }

    public ServiceRequest getRequest() { return request; }
    public void setRequest(ServiceRequest request) { this.request = request; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(LocalDateTime unassignedAt) { this.unassignedAt = unassignedAt; }
}