package com.civicconnect.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * One row-level change written by the audit trigger in the database (DEC-007).
 * The application only reads this table - @Immutable, no setters - and the database blocks UPDATE and
 * DELETE. The actor columns are plain IDs so the Reporting module does not depend on Access entities.
 */
@Entity
@Immutable
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @Column(name = "aud_id")
    private Long audId;

    @Column(name = "table_name", nullable = false, length = 50)
    private String table;

    @Column(name = "operation", nullable = false, length = 15)
    private String operation;

    @Column(name = "record_id", nullable = false, length = 20)
    private String recId;

    // JSON of the row before the change; null for INSERT
    @Column(name = "old_data")
    private String oldData;

    // JSON of the row after the change; null for DELETE
    @Column(name = "new_data")
    private String newData;

    // At most one of these is the actor; both null for system changes (A1)
    @Column(name = "emp_id")
    private Long empId;

    @Column(name = "client_id")
    private Long clientId;

    // UTC
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    protected AuditEntry() {}

    /** Probe for query-by-example searches only - never saved. */
    public static AuditEntry probe(String table, String recordId, String operation) {
        AuditEntry e = new AuditEntry();
        e.table = table;
        e.recId = recordId;
        e.operation = operation;
        return e;
    }

    public Long getAudId() { return audId; }
    public String getTable() { return table; }
    public String getOperation() { return operation; }
    public String getRecId() { return recId; }
    public String getOldData() { return oldData; }
    public String getNewData() { return newData; }
    public Long getEmpId() { return empId; }
    public Long getClientId() { return clientId; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
