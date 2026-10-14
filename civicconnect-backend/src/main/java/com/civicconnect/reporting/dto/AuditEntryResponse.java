package com.civicconnect.reporting.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * One audit entry from a per-table audit endpoint. oldData/newData are the row as JSON, limited to the
 * fields this table may show; changedFields lists the names of every field an UPDATE changed (names
 * only, so it is safe to show even where the values are hidden).
 */
public record AuditEntryResponse(
        Long audId,
        String resource,
        String operation,
        String recordId,
        JsonNode oldData,
        JsonNode newData,
        List<String> changedFields,
        Long empId,
        Long clientId,
        Instant changedAt
) {}
