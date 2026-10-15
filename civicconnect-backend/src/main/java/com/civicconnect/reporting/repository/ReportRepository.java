package com.civicconnect.reporting.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The Reporting module's read-only query repository (ARC-02 exception): plain SQL aggregates over the
 * request and category tables. It owns no entities and returns no personal fields (ASR-005).
 * Runs on the caller's transaction, so the user's database role applies (DbSessionInterceptor).
 */
@Repository
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One row per category, status and overdue flag, with the number of requests. */
    public List<StatusCount> countByCategoryAndStatus(LocalDateTime createdFrom, LocalDateTime createdBefore,
                                                      Long categoryId, String status, LocalDateTime overdueBefore) {
        StringBuilder sql = new StringBuilder("""
                select r.cat_id, r.status,
                       (r.status in ('NEW', 'ASSIGNED', 'IN_PROGRESS') and r.created_at < :overdueBefore) as overdue,
                       count(*) as n
                from request r
                where 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("overdueBefore", overdueBefore);
        if (createdFrom != null) {
            sql.append(" and r.created_at >= :createdFrom");
            params.addValue("createdFrom", createdFrom);
        }
        if (createdBefore != null) {
            sql.append(" and r.created_at < :createdBefore");
            params.addValue("createdBefore", createdBefore);
        }
        if (categoryId != null) {
            sql.append(" and r.cat_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        if (status != null) {
            sql.append(" and r.status = :status");
            params.addValue("status", status);
        }
        sql.append(" group by r.cat_id, r.status, overdue");
        return jdbc.query(sql.toString(), params, (rs, i) -> new StatusCount(
                rs.getLong("cat_id"), rs.getString("status"), rs.getBoolean("overdue"), rs.getLong("n")));
    }

    /** Category names for the report rows, including categories with no requests. */
    public List<CategoryName> categories(Long categoryId) {
        String sql = "select cat_id, cat_name from category"
                + (categoryId == null ? "" : " where cat_id = :categoryId") + " order by cat_name";
        return jdbc.query(sql, new MapSqlParameterSource("categoryId", categoryId),
                (rs, i) -> new CategoryName(rs.getLong("cat_id"), rs.getString("cat_name")));
    }

    public record StatusCount(long categoryId, String status, boolean overdue, long count) {}

    public record CategoryName(long categoryId, String name) {}
}
