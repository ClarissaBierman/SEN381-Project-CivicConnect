package com.civicconnect.reporting.service;

import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.time.Time;
import com.civicconnect.reporting.dto.ActivityReport;
import com.civicconnect.reporting.repository.ReportRepository;
import com.civicconnect.requests.domain.ServiceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-17: Management's activity counts (FR-012, FR-013). */
@Service
public class ReportService {

    /** CR-004: open for more than this many days = overdue. */
    static final int OVERDUE_DAYS = 7;

    private final ReportRepository reports;
    private final CurrentUserService currentUser;

    public ReportService(ReportRepository reports, CurrentUserService currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    /**
     * @param from       first UTC day of creation to include (optional)
     * @param to         last UTC day of creation to include (optional)
     */
    @Transactional(readOnly = true)
    public ActivityReport activity(LocalDate from, LocalDate to, Long categoryId, ServiceRequest.Status status) {
        if (from != null && to != null && from.isAfter(to)) {
            throw Errors.invalidField("from", "\"From\" must be on or before \"To\"");
        }

        LocalDateTime overdueBefore = Time.now().minusDays(OVERDUE_DAYS);
        List<ReportRepository.StatusCount> rows = reports.countByCategoryAndStatus(
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                categoryId, status == null ? null : status.name(), overdueBefore);

        Map<Long, Tally> byCategory = new LinkedHashMap<>();
        for (ReportRepository.CategoryName c : reports.categories(categoryId)) {
            byCategory.put(c.categoryId(), new Tally(c.name()));
        }
        Tally totals = new Tally(null);
        for (ReportRepository.StatusCount row : rows) {
            totals.add(row);
            byCategory.computeIfAbsent(row.categoryId(), id -> new Tally("Category " + id)).add(row);
        }

        List<ActivityReport.CategoryCounts> categories = byCategory.entrySet().stream()
                .map(e -> e.getValue().toCategory(e.getKey())).toList();
        return new ActivityReport(from, to, categoryId, status == null ? null : status.name(), Time.nowInstant(),
                totals.toCounts(), categories);
    }

    private static final class Tally {
        private final String name;
        private long total, open, overdue, resolved, closed, rejected;

        Tally(String name) { this.name = name; }

        void add(ReportRepository.StatusCount row) {
            total += row.count();
            switch (row.status()) {
                case "NEW", "ASSIGNED", "IN_PROGRESS" -> open += row.count();
                case "RESOLVED" -> resolved += row.count();
                case "CLOSED" -> closed += row.count();
                case "REJECTED" -> rejected += row.count();
                default -> { }
            }
            if (row.overdue()) overdue += row.count();
        }

        ActivityReport.Counts toCounts() {
            return new ActivityReport.Counts(total, open, overdue, resolved, closed, rejected);
        }

        ActivityReport.CategoryCounts toCategory(Long id) {
            return new ActivityReport.CategoryCounts(id, name, total, open, overdue, resolved, closed, rejected);
        }
    }
}
