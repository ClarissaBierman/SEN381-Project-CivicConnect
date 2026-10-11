package com.civicconnect.service;

import com.civicconnect.dto.AuditEntryResponse;
import com.civicconnect.model.AuditEntry;
import com.civicconnect.repository.AuditRepository;
import com.civicconnect.security.CurrentUser;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only access to the audit trail written by the database trigger (DEC-007, NFR-006). */
@Service
public class AuditService {

    private final AuditRepository auditRepository;
    private final RequestAccess access;
    private final CurrentUserService currentUser;
    private final DbSession db;

    public AuditService(AuditRepository auditRepository, RequestAccess access,
                        CurrentUserService currentUser, DbSession db) {
        this.auditRepository = auditRepository;
        this.access = access;
        this.currentUser = currentUser;
        this.db = db;
    }

    /** API-11: the full history of one request, oldest first (FR-007). */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> forRequest(Long requestId) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        access.checkStaffScope(access.load(requestId), user);
        return auditRepository.findRequestHistory(String.valueOf(requestId))
                .stream().map(AuditEntryResponse::from).toList();
    }

    /** Management search over the whole audit trail. All filters are optional. */
    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> search(String table, String recordId, String operation, Pageable pageable) {
        db.begin(currentUser.get());

        AuditEntry probe = new AuditEntry();
        probe.setTable(table);
        probe.setRecId(recordId);
        probe.setOperation(operation);
        probe.setChangedAt(null); // the entity defaults this to now(), which would filter everything out

        Example<AuditEntry> example = Example.of(probe, ExampleMatcher.matching()
                .withIgnoreNullValues()
                .withIgnoreCase("table", "operation"));
        return auditRepository.findAll(example, pageable).map(AuditEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public AuditEntryResponse get(Long id) {
        db.begin(currentUser.get());
        return auditRepository.findById(id).map(AuditEntryResponse::from)
                .orElseThrow(() -> Errors.notFound("Audit entry"));
    }
}
