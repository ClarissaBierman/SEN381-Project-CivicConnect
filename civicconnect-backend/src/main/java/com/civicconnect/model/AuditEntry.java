package com.civicconnect.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row-level change written by the audit trigger in the database (DEC-007).
 * The application only reads this table - it never inserts, updates or deletes audit rows.
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aud_id")
    private Long audId;

    @Column(name = "table_name", nullable = false, length = 50)
    private String table;

    @Column(name = "operation", nullable = false, length = 15)
    private String operation;

    @Column(name = "record_id", nullable = false, length = 20)
    private String recId;

    // Null for INSERT (there was no old row)
    @Column(name = "old_data")
    private String oldData;

    // Null for DELETE (there is no new row)
    @Column(name = "new_data")
    private String newData;

    // Exactly one of employee / client is the actor; both are null for system changes (A1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id")
    private Employee empId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client clientId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public AuditEntry() {
    }

    public Long getAudId() {
        return audId;
    }

    public void setAudId(Long audId) {
        this.audId = audId;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getRecId() {
        return recId;
    }

    public void setRecId(String recId) {
        this.recId = recId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOldData() {
        return oldData;
    }

    public void setOldData(String oldData) {
        this.oldData = oldData;
    }

    public String getNewData() {
        return newData;
    }

    public void setNewData(String newData) {
        this.newData = newData;
    }

    public Employee getEmpId() {
        return empId;
    }

    public void setEmpId(Employee empId) {
        this.empId = empId;
    }

    public Client getClientId() {
        return clientId;
    }

    public void setClientId(Client clientId) {
        this.clientId = clientId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
