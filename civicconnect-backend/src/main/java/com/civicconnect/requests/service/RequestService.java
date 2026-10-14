package com.civicconnect.requests.service;

import com.civicconnect.access.domain.Client;
import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.security.DbSession;
import com.civicconnect.common.time.Time;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.domain.Comment;
import com.civicconnect.requests.domain.RequestHandler;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.domain.ServiceRequest.Priority;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import com.civicconnect.requests.dto.AssignmentRequest;
import com.civicconnect.requests.dto.NewServiceRequest;
import com.civicconnect.requests.dto.RequestDetail;
import com.civicconnect.requests.dto.RequestSummary;
import com.civicconnect.requests.dto.StatusChangeRequest;
import com.civicconnect.requests.repository.CategoryRepository;
import com.civicconnect.requests.repository.CommentRepository;
import com.civicconnect.requests.repository.RequestHandlerRepository;
import com.civicconnect.requests.repository.RequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Requests module's business rules (ARC-02): submitting, listing, viewing, status changes,
 * assignment and priority. Every change is one transaction (DEC-006) that starts by row-locking the
 * request, so concurrent changes to the same request are applied one after the other; the audit
 * trigger writes the audit rows inside the same transaction (DEC-007).
 */
@Service
public class RequestService {

    @PersistenceContext
    private EntityManager entityManager;

    private final RequestRepository requestRepository;
    private final CategoryRepository categoryRepository;
    private final RequestHandlerRepository requestHandlerRepository;
    private final CommentRepository commentRepository;
    private final StaffDirectory staff;
    private final TransitionPolicy transitionPolicy;
    private final RequestAccess access;
    private final CommentViews commentViews;
    private final CurrentUserService currentUser;
    private final DbSession db;

    public RequestService(RequestRepository requestRepository, CategoryRepository categoryRepository,
                          RequestHandlerRepository requestHandlerRepository, CommentRepository commentRepository,
                          StaffDirectory staff, TransitionPolicy transitionPolicy, RequestAccess access,
                          CommentViews commentViews, CurrentUserService currentUser, DbSession db) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.requestHandlerRepository = requestHandlerRepository;
        this.commentRepository = commentRepository;
        this.staff = staff;
        this.transitionPolicy = transitionPolicy;
        this.access = access;
        this.commentViews = commentViews;
        this.currentUser = currentUser;
        this.db = db;
    }

    /** API-04: a requester submits a new request (FR-001, FR-015). */
    @Transactional
    public RequestDetail create(NewServiceRequest body) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        Category category = categoryRepository.findById(body.categoryId())
                .orElseThrow(() -> Errors.invalidField("categoryId", "Category does not exist"));
        if (!category.isActive()) {
            throw Errors.rule(ErrorCode.CATEGORY_INACTIVE, "This category is no longer available.");
        }

        ServiceRequest request = new ServiceRequest();
        request.setRefNum(String.format("CC-%d-%06d", Time.currentYear().getValue(), requestRepository.nextRefNumber()));
        request.setCategory(category);
        request.setDescription(body.description().trim());
        request.setLocation(body.location().trim());
        request.setAttachmentUrl(body.attachmentUrl());
        request.setRequester(entityManager.getReference(Client.class, user.id()));
        request.setStatus(Status.NEW);
        requestRepository.save(request);

        return toDetail(request, user);
    }

    /** API-05: the requester's own requests, newest first by default (FR-003). */
    @Transactional(readOnly = true)
    public Page<RequestSummary> mine(Pageable pageable) {
        CurrentUser user = currentUser.get();
        db.begin(user);
        return requestRepository.findByRequester(user.id(), pageable).map(RequestSummary::from);
    }

    /** API-06: one request, if the user may see it (FR-002, FR-007). */
    @Transactional(readOnly = true)
    public RequestDetail get(Long id) {
        CurrentUser user = currentUser.get();
        db.begin(user);
        return toDetail(access.loadViewable(id, user), user);
    }

    /** API-07: staff see their categories only, managers see all (FR-005, FR-006). */
    @Transactional(readOnly = true)
    public Page<RequestSummary> list(Status status, Long categoryId, LocalDate from, LocalDate to, Pageable pageable) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        Specification<ServiceRequest> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("catId"), categoryId));
        }
        // Dates are UTC calendar days, like every stored timestamp
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
        }
        if (user.isStaff()) {
            List<Long> scope = staff.categoryIds(user.id());
            if (scope.isEmpty()) {
                return Page.empty(pageable);
            }
            spec = spec.and((root, query, cb) -> root.get("category").get("catId").in(scope));
        }

        Page<ServiceRequest> page = requestRepository.findAll(spec, pageable);
        Map<Long, Long> handlerByRequest = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] row : requestHandlerRepository.findActiveHandlerIds(
                    page.getContent().stream().map(ServiceRequest::getReqId).toList())) {
                handlerByRequest.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, String> names = staff.names(handlerByRequest.values().stream().distinct().toList());
        return page.map(r -> {
            Long handlerId = handlerByRequest.get(r.getReqId());
            return RequestSummary.from(r, handlerId, handlerId == null ? null : names.get(handlerId));
        });
    }

    /** API-09: change status through the state machine (FR-009, FR-011, DEC-011). */
    @Transactional
    public RequestDetail changeStatus(Long id, StatusChangeRequest body) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        ServiceRequest request = access.loadForUpdate(id);
        access.checkStaffScope(request, user);

        Status from = request.getStatus();
        Status to = body.toStatus();
        if (transitionPolicy.isTerminal(from)) {
            throw Errors.rule(ErrorCode.REQUEST_CLOSED, "Closed or rejected requests are read-only.");
        }
        if (to == Status.ASSIGNED) {
            throw Errors.rule(ErrorCode.INVALID_TRANSITION, "Use the assignment endpoint to assign a request.");
        }
        if (!transitionPolicy.canMove(from, to)) {
            throw Errors.rule(ErrorCode.INVALID_TRANSITION, "A request cannot move from " + from + " to " + to + ".");
        }
        // After assignment, only the active handler (or a manager) may move the request on
        if (user.isStaff() && from != Status.NEW && !isActiveHandler(request, user.id())) {
            throw Errors.forbidden("Only the assigned handler can change this request.");
        }

        LocalDateTime now = Time.now();
        request.setStatus(to);
        request.setUpdatedAt(now);
        if (transitionPolicy.isTerminal(to)) {
            request.setClosedAt(now);
        }

        if (body.note() != null && !body.note().isBlank()) {
            commentRepository.save(new Comment(request, entityManager.getReference(Employee.class, user.id()),
                    body.note().trim(), Boolean.TRUE.equals(body.noteVisibleToRequester())));
        }
        return toDetail(request, user);
    }

    /**
     * API-08: staff accept a New request themselves; managers assign or reassign any active handler
     * who covers the category (FR-008). Only one active handler is allowed - the row lock serialises
     * two accepts on the same request, and the database's partial unique index is the final guard.
     */
    @Transactional
    public RequestDetail assign(Long id, AssignmentRequest body) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        ServiceRequest request = access.loadForUpdate(id);
        access.checkStaffScope(request, user);

        Status status = request.getStatus();
        if (transitionPolicy.isTerminal(status)) {
            throw Errors.rule(ErrorCode.REQUEST_CLOSED, "Closed or rejected requests are read-only.");
        }
        if (status == Status.RESOLVED) {
            throw Errors.rule(ErrorCode.INVALID_TRANSITION, "A resolved request cannot be reassigned.");
        }

        Long handlerId;
        if (user.isStaff()) {
            if (body != null && body.employeeId() != null && !body.employeeId().equals(user.id())) {
                throw Errors.forbidden("Staff can only assign requests to themselves.");
            }
            handlerId = user.id();
        } else {
            if (body == null || body.employeeId() == null) {
                throw Errors.invalidField("employeeId", "must not be null");
            }
            Employee handler = staff.find(body.employeeId())
                    .orElseThrow(() -> Errors.invalidField("employeeId", "Employee does not exist"));
            if (handler.getRole() != Employee.Role.STAFF || !handler.isActive()) {
                throw Errors.invalidField("employeeId", "Requests can only be assigned to active staff members");
            }
            if (!access.inScope(handler.getEmpId(), request.getCategory().getCatId())) {
                throw Errors.rule(ErrorCode.OUT_OF_SCOPE, "That handler does not cover this category.");
            }
            handlerId = handler.getEmpId();
        }

        LocalDateTime now = Time.now();
        RequestHandler current = requestHandlerRepository.findActive(id).orElse(null);
        if (current != null) {
            if (user.isStaff()) {
                throw Errors.conflict(ErrorCode.ALREADY_ASSIGNED, "This request is already assigned.");
            }
            if (current.getEmployee().getEmpId().equals(handlerId)) {
                throw Errors.conflict(ErrorCode.ALREADY_ASSIGNED, "This request is already assigned to that handler.");
            }
            current.setUnassignedAt(now);
            // Flush now so the old row is closed before the new active row is inserted
            requestHandlerRepository.saveAndFlush(current);
        }

        requestHandlerRepository.save(new RequestHandler(request, entityManager.getReference(Employee.class, handlerId)));
        if (status == Status.NEW) {
            request.setStatus(Status.ASSIGNED);
        }
        request.setUpdatedAt(now);

        try {
            requestHandlerRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw Errors.conflict(ErrorCode.ALREADY_ASSIGNED, "This request is already assigned.");
        }
        return toDetail(request, user);
    }

    /** API-12: Management sets the priority (FR-016). The audit trigger records the change. */
    @Transactional
    public RequestDetail setPriority(Long id, Priority priority) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        ServiceRequest request = access.loadForUpdate(id);
        if (transitionPolicy.isTerminal(request.getStatus())) {
            throw Errors.rule(ErrorCode.REQUEST_CLOSED, "Closed or rejected requests are read-only.");
        }
        if (request.getPriority() != priority) {
            request.setPriority(priority);
            request.setUpdatedAt(Time.now());
        }
        return toDetail(request, user);
    }

    // ---------------------------------------------------------------- helpers

    private boolean isActiveHandler(ServiceRequest request, Long empId) {
        return requestHandlerRepository.findActive(request.getReqId())
                .map(rh -> rh.getEmployee().getEmpId().equals(empId))
                .orElse(false);
    }

    private RequestDetail toDetail(ServiceRequest r, CurrentUser user) {
        Long handlerId = null;
        String handlerName = null;
        Set<Status> allowed = null;
        if (user.isEmployee()) {
            handlerId = requestHandlerRepository.findActive(r.getReqId())
                    .map(rh -> rh.getEmployee().getEmpId()).orElse(null);
            if (handlerId != null) {
                handlerName = staff.names(List.of(handlerId)).get(handlerId);
            }
            allowed = transitionPolicy.allowedFrom(r.getStatus());
            allowed.remove(Status.ASSIGNED); // assigning has its own endpoint
        }

        return new RequestDetail(r.getReqId(), r.getRefNum(), r.getCategory().getCatId(), r.getCategory().getCatName(),
                r.getDescription(), r.getLocation(), r.getAttachmentUrl(), r.getStatus(), r.getPriority(),
                r.getRequester().getClientId(), handlerId, handlerName,
                Time.toInstant(r.getCreatedAt()), Time.toInstant(r.getUpdatedAt()), Time.toInstant(r.getClosedAt()),
                allowed, commentViews.forRequest(r.getReqId(), user));
    }
}
