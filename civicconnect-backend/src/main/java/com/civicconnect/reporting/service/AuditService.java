package com.civicconnect.reporting.service;

import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.time.Time;
import com.civicconnect.reporting.domain.AuditEntry;
import com.civicconnect.reporting.domain.AuditResource;
import com.civicconnect.reporting.dto.AuditEntryResponse;
import com.civicconnect.reporting.dto.RequestHistoryEntry;
import com.civicconnect.reporting.repository.AuditRepository;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.service.RequestAccess;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the audit trail. Two kinds of view:
 *  - a request's history (API-11) for staff in scope and managers, described for people;
 *  - per-table audit lists (/audit/{resource}) for managers, where client entries never show personal
 *    values (see AuditResource).
 */
@Service
public class AuditService {

    private static final String SYSTEM_ACTOR = "System";

    private final AuditRepository auditRepository;
    private final RequestAccess access;
    private final StaffDirectory staff;
    private final CurrentUserService currentUser;
    private final JsonMapper json = JsonMapper.builder().build();

    public AuditService(AuditRepository auditRepository, RequestAccess access, StaffDirectory staff,
                        CurrentUserService currentUser) {
        this.auditRepository = auditRepository;
        this.access = access;
        this.staff = staff;
        this.currentUser = currentUser;
    }

    /** API-11: full history of one request, oldest first (FR-007, NFR-006). */
    @Transactional(readOnly = true)
    public List<RequestHistoryEntry> forRequest(Long requestId) {
        CurrentUser user = currentUser.get();
        access.checkStaffScope(access.load(requestId), user);

        List<AuditEntry> entries = auditRepository.findRequestHistory(String.valueOf(requestId));
        Set<Long> empIds = new TreeSet<>();
        for (AuditEntry e : entries) {
            if (e.getEmpId() != null) empIds.add(e.getEmpId());
            if ("request_handler".equals(e.getTable())) {
                Long handler = longField(parse(e.getNewData() != null ? e.getNewData() : e.getOldData()), "emp_id");
                if (handler != null) empIds.add(handler);
            }
        }
        Map<Long, String> names = staff.names(empIds);

        List<RequestHistoryEntry> history = new ArrayList<>();
        for (AuditEntry e : entries) {
            history.add(describe(e, names));
        }
        return history;
    }

    /** /audit/{resource}: audit entries of one table, newest first by default. Management only. */
    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> search(AuditResource resource, String recordId, String operation, Pageable pageable) {
        AuditEntry probe = AuditEntry.probe(resource.table(), recordId,
                operation == null ? null : operation.trim().toUpperCase());
        Example<AuditEntry> example = Example.of(probe, ExampleMatcher.matching()
                .withIgnoreNullValues()
                .withIgnorePaths("audId", "empId", "clientId", "changedAt", "oldData", "newData"));
        return auditRepository.findAll(example, pageable).map(e -> toResponse(e, resource));
    }

    /** One audit entry, only if it belongs to the requested table (so /audit/requests/{id} cannot show a client row). */
    @Transactional(readOnly = true)
    public AuditEntryResponse get(AuditResource resource, Long auditId) {
        return auditRepository.findById(auditId)
                .filter(e -> e.getTable().equals(resource.table()))
                .map(e -> toResponse(e, resource))
                .orElseThrow(() -> Errors.notFound("Audit entry"));
    }

    // ---------------------------------------------------------------- per-table view

    private AuditEntryResponse toResponse(AuditEntry e, AuditResource resource) {
        JsonNode oldData = parse(e.getOldData());
        JsonNode newData = parse(e.getNewData());
        return new AuditEntryResponse(e.getAudId(), resource.slug(), e.getOperation(), e.getRecId(),
                visible(oldData, resource), visible(newData, resource), changedFields(oldData, newData),
                e.getEmpId(), e.getClientId(), Time.toInstant(e.getChangedAt()));
    }

    /** Removes the fields this table may not show (client personal data). */
    private JsonNode visible(JsonNode data, AuditResource resource) {
        if (data == null || resource.showsAllFields() || !data.isObject()) return data;
        ObjectNode copy = ((ObjectNode) data).deepCopy();
        List<String> hidden = new ArrayList<>();
        for (String name : copy.propertyNames()) {
            if (!resource.shows(name)) hidden.add(name);
        }
        hidden.forEach(copy::remove);
        return copy;
    }

    /** Names of the fields an UPDATE changed; empty for INSERT and DELETE. */
    private static List<String> changedFields(JsonNode oldData, JsonNode newData) {
        if (oldData == null || newData == null || !oldData.isObject() || !newData.isObject()) return List.of();
        Set<String> names = new TreeSet<>();
        oldData.propertyNames().forEach(names::add);
        newData.propertyNames().forEach(names::add);
        names.remove("password_changed");
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (!Objects.equals(oldData.get(name), newData.get(name))) changed.add(name);
        }
        if (newData.has("password_changed")) changed.add("password");
        return changed;
    }

    // ---------------------------------------------------------------- request history

    private RequestHistoryEntry describe(AuditEntry e, Map<Long, String> names) {
        JsonNode oldData = parse(e.getOldData());
        JsonNode newData = parse(e.getNewData());
        ServiceRequest.Status from = null;
        ServiceRequest.Status to = null;
        String action;

        switch (e.getTable()) {
            case "request" -> {
                if ("INSERT".equals(e.getOperation())) {
                    action = "submitted the request";
                    to = status(newData);
                } else {
                    ServiceRequest.Status before = status(oldData);
                    ServiceRequest.Status after = status(newData);
                    String oldPriority = text(oldData, "priority");
                    String newPriority = text(newData, "priority");
                    if (before != after) {
                        action = "changed the status";
                        from = before;
                        to = after;
                    } else if (!Objects.equals(oldPriority, newPriority)) {
                        action = newPriority == null ? "cleared the priority"
                                : "set the priority to " + newPriority.charAt(0) + newPriority.substring(1).toLowerCase();
                    } else {
                        action = "updated the request";
                    }
                }
            }
            case "request_handler" -> {
                Long handler = longField(newData != null ? newData : oldData, "emp_id");
                String who = handler == null ? "a handler" : names.getOrDefault(handler, "employee " + handler);
                boolean unassigned = newData != null && newData.hasNonNull("unassigned_at")
                        && (oldData == null || !oldData.hasNonNull("unassigned_at"));
                action = unassigned ? "removed " + who + " as handler" : "assigned " + who + " as handler";
            }
            case "comment" -> {
                boolean visibleToRequester = newData != null && newData.path("visible_to_requester").asBoolean(false);
                action = visibleToRequester ? "added a note for the requester" : "added an internal note";
            }
            default -> action = e.getOperation().toLowerCase() + " " + e.getTable();
        }

        return new RequestHistoryEntry(e.getAudId(), Time.toInstant(e.getChangedAt()), actor(e, names),
                e.getEmpId(), e.getClientId(), action, from, to);
    }

    private static String actor(AuditEntry e, Map<Long, String> names) {
        if (e.getEmpId() != null) return names.getOrDefault(e.getEmpId(), "Employee " + e.getEmpId());
        if (e.getClientId() != null) return "Requester #" + e.getClientId();
        return SYSTEM_ACTOR;
    }

    private JsonNode parse(String data) {
        if (data == null) return null;
        try {
            return json.readTree(data);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static ServiceRequest.Status status(JsonNode data) {
        String s = text(data, "status");
        if (s == null) return null;
        try {
            return ServiceRequest.Status.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode data, String field) {
        return data == null || !data.hasNonNull(field) ? null : data.get(field).asString();
    }

    private static Long longField(JsonNode data, String field) {
        return data == null || !data.hasNonNull(field) ? null : data.get(field).asLong();
    }
}
