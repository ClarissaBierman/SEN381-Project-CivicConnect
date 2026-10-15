package com.civicconnect.requests.domain;

import com.civicconnect.common.time.Time;
import com.civicconnect.access.domain.Client;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "request")
public class ServiceRequest {

    public enum Status { NEW, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, REJECTED }

    /** Requests still being worked on (FR-012, API-17 "open"). */
    public static final Set<Status> OPEN_STATUSES = Set.copyOf(EnumSet.of(Status.NEW, Status.ASSIGNED, Status.IN_PROGRESS));

    /** FR-016 (CR-003). Set by Management only; null until a manager sets it. */
    public enum Priority { LOW, MEDIUM, HIGH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "req_id")
    private Long reqId;

    @Column(name = "ref_num", nullable = false, unique = true, length = 50)
    private String refNum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cat_id", nullable = false)
    private Category category;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Client requester;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = Time.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = Time.now();

    public ServiceRequest() {}

    public Long getReqId() { return reqId; }
    public void setReqId(Long reqId) { this.reqId = reqId; }

    public String getRefNum() { return refNum; }
    public void setRefNum(String refNum) { this.refNum = refNum; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Client getRequester() { return requester; }
    public void setRequester(Client requester) { this.requester = requester; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
}
