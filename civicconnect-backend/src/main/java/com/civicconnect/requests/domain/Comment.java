package com.civicconnect.requests.domain;

import com.civicconnect.common.time.Time;
import com.civicconnect.access.domain.Employee;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * A staff comment or resolution note on a request (FR-010).
 * Append-only: @Immutable stops Hibernate from updating it, and a database trigger
 * blocks UPDATE and DELETE (DEC-007).
 */
@Entity
@Immutable
@Table(name = "comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "com_id")
    private Long comId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "req_id", nullable = false)
    private ServiceRequest request;

    // Author - always a staff member or manager
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    // FR-002: requesters only see comments marked visible to them
    @Column(name = "visible_to_requester", nullable = false)
    private boolean visibleToRequester = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = Time.now();

    public Comment() {}

    public Comment(ServiceRequest request, Employee employee, String content, boolean visibleToRequester) {
        this.request = request;
        this.employee = employee;
        this.content = content;
        this.visibleToRequester = visibleToRequester;
    }

    public Long getComId() { return comId; }
    public void setComId(Long comId) { this.comId = comId; }

    public ServiceRequest getRequest() { return request; }
    public void setRequest(ServiceRequest request) { this.request = request; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isVisibleToRequester() { return visibleToRequester; }
    public void setVisibleToRequester(boolean visibleToRequester) { this.visibleToRequester = visibleToRequester; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
