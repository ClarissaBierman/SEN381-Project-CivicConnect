package com.civicconnect.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * API-17: counts only - no names, contact details or request text (ASR-005, POPIA).
 * open = New + Assigned + In Progress; overdue = open and created more than 7 days ago (CR-004).
 * The exact figures to report are still to be decided; add fields here without removing these.
 */
public record ActivityReport(
        LocalDate from,
        LocalDate to,
        Long categoryId,
        String status,
        Instant generatedAt,
        Counts totals,
        List<CategoryCounts> byCategory
) {
    public record Counts(long total, long open, long overdue, long resolved, long closed, long rejected) {}

    public record CategoryCounts(Long categoryId, String categoryName, long total, long open, long overdue,
                                 long resolved, long closed, long rejected) {}
}
