package com.civicconnect.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One assignment of a handler to a request. A request can be reassigned, so it can have
 * several rows over time, but only one with unassigned_at = null (the active handler).
 */
@Entity
@Table(name = "request_handler")
public class RequestHandler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "req_handler_id")
    private Long reqHandlerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "req_id", nullable = false)
    private ServiceRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    public RequestHandler() {}

    public RequestHandler(ServiceRequest request, Employee employee) {
        this.request = request;
        this.employee = employee;
    }

    public Long getReqHandlerId() { return reqHandlerId; }
    public void setReqHandlerId(Long reqHandlerId) { this.reqHandlerId = reqHandlerId; }

    public ServiceRequest getRequest() { return request; }
    public void setRequest(ServiceRequest request) { this.request = request; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(LocalDateTime unassignedAt) { this.unassignedAt = unassignedAt; }
}
