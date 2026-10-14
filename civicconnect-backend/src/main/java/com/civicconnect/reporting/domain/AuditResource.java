package com.civicconnect.reporting.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The per-table audit endpoints: /api/v1/audit/{slug}. Each maps to one audited table and says which
 * JSON fields may be shown. For clients only non-personal fields are shown (ASR-005): Management sees
 * that a client changed their email or was deactivated, and by whom, but not the values.
 */
public enum AuditResource {

    REQUESTS("requests", "request", null),
    ASSIGNMENTS("assignments", "request_handler", null),
    COMMENTS("comments", "comment", null),
    CATEGORIES("categories", "category", null),
    HANDLER_CATEGORIES("handler-categories", "handler_category", null),
    EMPLOYEES("employees", "employee", null),
    CLIENTS("clients", "client", Set.of("client_id", "active", "created_at", "updated_at", "password_changed"));

    private final String slug;
    private final String table;
    /** null = every field may be shown */
    private final Set<String> visibleFields;

    AuditResource(String slug, String table, Set<String> visibleFields) {
        this.slug = slug;
        this.table = table;
        this.visibleFields = visibleFields;
    }

    public String slug() { return slug; }

    public String table() { return table; }

    public boolean showsAllFields() { return visibleFields == null; }

    public boolean shows(String field) { return visibleFields == null || visibleFields.contains(field); }

    public static Optional<AuditResource> fromSlug(String slug) {
        return Arrays.stream(values()).filter(r -> r.slug.equals(slug)).findFirst();
    }

    public static String slugs() {
        return String.join(", ", Arrays.stream(values()).map(AuditResource::slug).toList());
    }
}
