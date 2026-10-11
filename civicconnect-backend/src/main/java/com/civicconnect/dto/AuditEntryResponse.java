package com.civicconnect.dto;

import com.civicconnect.model.AuditEntry;

import java.time.LocalDateTime;

/**
 * What the API returns for one audit entry.
 * Only the actor IDs are returned, not the whole Employee or Client objects,
 * so personal fields are never sent to the browser (ASR-005).
 */
public record AuditEntryResponse(
        Long audId,
        String table,
        String operation,
        String recordId,
        String oldData,
        String newData,
        Long empId,
        Long clientId,
        LocalDateTime changedAt
) {

    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.getAudId(),
                entry.getTable(),
                entry.getOperation(),
                entry.getRecId(),
                entry.getOldData(),
                entry.getNewData(),
                entry.getEmpId() != null ? entry.getEmpId().getEmpId() : null,
                entry.getClientId() != null ? entry.getClientId().getClientId() : null,
                entry.getChangedAt()
        );
    }
}
