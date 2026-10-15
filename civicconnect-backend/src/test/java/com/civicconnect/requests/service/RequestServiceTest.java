package com.civicconnect.requests.service;

import com.civicconnect.access.domain.Client;
import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.access.repository.HandlerCategoryRepository;
import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.domain.Comment;
import com.civicconnect.requests.domain.RequestHandler;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.dto.AssignmentRequest;
import com.civicconnect.requests.dto.NewServiceRequest;
import com.civicconnect.requests.dto.RequestDetail;
import com.civicconnect.requests.dto.StatusChangeRequest;
import com.civicconnect.requests.repository.CategoryRepository;
import com.civicconnect.requests.repository.CommentRepository;
import com.civicconnect.requests.repository.RequestHandlerRepository;
import com.civicconnect.requests.repository.RequestRepository;

import com.civicconnect.requests.domain.ServiceRequest.Priority;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import jakarta.persistence.EntityManager;
import com.civicconnect.requests.dto.RequestSummary;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Business rules of the Requests module (FR-001, FR-003, FR-005, FR-008, FR-009, FR-011, DEC-011).
 * Pure unit tests: repositories are mocked, so no database is needed.
 */
class RequestServiceTest {

    private RequestRepository requestRepository;
    private CategoryRepository categoryRepository;
    private EmployeeRepository employeeRepository;
    private RequestHandlerRepository handlerRepository;
    private CommentRepository commentRepository;
    private HandlerCategoryRepository scopeRepository;
    private CurrentUserService currentUser;
    private RequestService service;

    private final Category itSupport = category(4, "IT Support", true);

    @BeforeEach
    void setUp() {
        requestRepository = mock(RequestRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        handlerRepository = mock(RequestHandlerRepository.class);
        commentRepository = mock(CommentRepository.class);
        scopeRepository = mock(HandlerCategoryRepository.class);
        currentUser = mock(CurrentUserService.class);

        StaffDirectory staff = new StaffDirectory(employeeRepository, scopeRepository);
        RequestAccess access = new RequestAccess(requestRepository, staff);
        service = new RequestService(requestRepository, categoryRepository, handlerRepository, commentRepository,
                staff, new TransitionPolicy(), access, new CommentViews(commentRepository, staff), currentUser);

        // getReference() returns a stand-in entity with just the ID, like Hibernate does
        EntityManager em = mock(EntityManager.class);
        when(em.getReference(eq(Client.class), any())).thenAnswer(i -> client((Long) i.getArgument(1)));
        when(em.getReference(eq(Employee.class), any()))
                .thenAnswer(i -> employee((Long) i.getArgument(1), Employee.Role.STAFF, true));
        ReflectionTestUtils.setField(service, "entityManager", em);

        // Defaults: no comments, no active handler; staff 3 covers IT Support (4) and Damaged Equipment (2)
        when(commentRepository.findAllForRequest(anyLong())).thenReturn(List.of());
        when(commentRepository.findVisibleForRequest(anyLong())).thenReturn(List.of());
        when(handlerRepository.findActive(anyLong())).thenReturn(Optional.empty());
        when(scopeRepository.findCategoryIds(3L)).thenReturn(List.of(2L, 4L));
        when(scopeRepository.findCategoryIds(4L)).thenReturn(List.of(3L, 6L));
    }

    private void loggedInAs(CurrentUser user) {
        when(currentUser.get()).thenReturn(user);
    }

    private ServiceRequest existing(long id, Status status) {
        ServiceRequest r = request(id, status, itSupport, 1);
        when(requestRepository.findById(id)).thenReturn(Optional.of(r));
        when(requestRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        return r;
    }

    private void activeHandler(ServiceRequest request, long empId) {
        RequestHandler rh = new RequestHandler(request, employee(empId, Employee.Role.STAFF, true));
        when(handlerRepository.findActive(request.getReqId())).thenReturn(Optional.of(rh));
    }

    // ------------------------------------------------------------------ submitting (API-04)

    @Test
    @DisplayName("Submitting sets the reference number, requester and status on the server (FR-001)")
    void createSetsServerControlledFields() {
        loggedInAs(requester(1));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(itSupport));
        when(requestRepository.nextRefNumber()).thenReturn(13L);

        RequestDetail detail = service.create(new NewServiceRequest(4L, "  Printer jammed  ", " Office 3 ", null));

        ArgumentCaptor<ServiceRequest> saved = ArgumentCaptor.forClass(ServiceRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().getRefNum()).isEqualTo("CC-" + Year.now().getValue() + "-000013");
        assertThat(saved.getValue().getStatus()).isEqualTo(Status.NEW);
        assertThat(saved.getValue().getRequester().getClientId()).isEqualTo(1L);
        assertThat(saved.getValue().getDescription()).isEqualTo("Printer jammed");
        assertThat(detail.allowedTransitions()).as("requesters never get allowedTransitions").isNull();
    }

    @Test
    @DisplayName("A request cannot be submitted in an inactive category (FR-015)")
    void createRejectsInactiveCategory() {
        loggedInAs(requester(1));
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category(8, "Parking", false)));

        assertApiError(ErrorCode.CATEGORY_INACTIVE,
                () -> service.create(new NewServiceRequest(8L, "Lines faded", "Parking", null)));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("An unknown category is a validation error on the categoryId field")
    void createRejectsUnknownCategory() {
        loggedInAs(requester(1));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> service.create(new NewServiceRequest(99L, "x", "y", null)));
        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("categoryId");
    }

    // ------------------------------------------------------------------ viewing (API-06)

    @Test
    @DisplayName("A requester cannot see another client's request - it looks like it does not exist (FR-003)")
    void requesterCannotSeeOtherClientsRequest() {
        loggedInAs(requester(2));
        existing(10, Status.NEW); // belongs to client 1

        assertApiError(ErrorCode.NOT_FOUND, () -> service.get(10L));
    }

    @Test
    @DisplayName("Staff cannot open a request outside their categories (FR-005)")
    void staffOutsideCategoryIsForbidden() {
        loggedInAs(staff(4)); // Sipho covers Security Concerns and Lost Property only
        existing(10, Status.NEW);

        assertApiError(ErrorCode.FORBIDDEN, () -> service.get(10L));
    }

    @Test
    @DisplayName("Requesters only receive comments marked visible to them (FR-002)")
    void requesterOnlyGetsVisibleComments() {
        loggedInAs(requester(1));
        ServiceRequest r = existing(10, Status.IN_PROGRESS);
        Comment visible = new Comment(r, employee(3, Employee.Role.STAFF, true), "Part ordered", true);
        when(commentRepository.findVisibleForRequest(10L)).thenReturn(List.of(visible));

        RequestDetail detail = service.get(10L);

        assertThat(detail.comments()).extracting("content").containsExactly("Part ordered");
        verify(commentRepository, never()).findAllForRequest(anyLong());
        verify(handlerRepository, never()).findActive(anyLong()); // requesters never see who handles it
    }

    @Test
    @DisplayName("Staff get the allowed next statuses, without Assigned (it has its own endpoint)")
    void staffGetAllowedTransitions() {
        loggedInAs(staff(3));
        existing(10, Status.NEW);

        assertThat(service.get(10L).allowedTransitions()).containsExactly(Status.REJECTED);
    }

    // ------------------------------------------------------------------ status changes (API-09)

    @Test
    @DisplayName("A status cannot skip steps (FR-009, DEC-011)")
    void cannotSkipSteps() {
        loggedInAs(manager(1));
        existing(10, Status.NEW);

        assertApiError(ErrorCode.INVALID_TRANSITION,
                () -> service.changeStatus(10L, new StatusChangeRequest(Status.RESOLVED, null, null)));
    }

    @Test
    @DisplayName("Closed and rejected requests are read-only (FR-011)")
    void closedRequestIsReadOnly() {
        loggedInAs(manager(1));
        existing(10, Status.CLOSED);

        assertApiError(ErrorCode.REQUEST_CLOSED,
                () -> service.changeStatus(10L, new StatusChangeRequest(Status.IN_PROGRESS, null, null)));
    }

    @Test
    @DisplayName("Assigned can only be reached through the assignment endpoint")
    void cannotSetAssignedThroughStatusEndpoint() {
        loggedInAs(manager(1));
        existing(10, Status.NEW);

        assertApiError(ErrorCode.INVALID_TRANSITION,
                () -> service.changeStatus(10L, new StatusChangeRequest(Status.ASSIGNED, null, null)));
    }

    @Test
    @DisplayName("Only the assigned handler can move an assigned request on")
    void otherStaffCannotChangeAssignedRequest() {
        loggedInAs(staff(3));
        ServiceRequest r = existing(10, Status.ASSIGNED);
        activeHandler(r, 99);

        assertApiError(ErrorCode.FORBIDDEN,
                () -> service.changeStatus(10L, new StatusChangeRequest(Status.IN_PROGRESS, null, null)));
    }

    @Test
    @DisplayName("Closing sets closedAt and updatedAt")
    void closingSetsClosedAt() {
        loggedInAs(staff(3));
        ServiceRequest r = existing(10, Status.RESOLVED);
        activeHandler(r, 3);

        RequestDetail detail = service.changeStatus(10L, new StatusChangeRequest(Status.CLOSED, null, null));

        assertThat(detail.status()).isEqualTo(Status.CLOSED);
        assertThat(r.getClosedAt()).isNotNull();
        assertThat(detail.allowedTransitions()).isEmpty();
    }

    @Test
    @DisplayName("A note with a status change is saved as a comment in the same transaction")
    void noteIsSavedAsComment() {
        loggedInAs(staff(3));
        ServiceRequest r = existing(10, Status.ASSIGNED);
        activeHandler(r, 3);

        service.changeStatus(10L, new StatusChangeRequest(Status.IN_PROGRESS, " Ordering part ", true));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("Ordering part");
        assertThat(saved.getValue().isVisibleToRequester()).isTrue();
        assertThat(saved.getValue().getEmployee().getEmpId()).isEqualTo(3L);
        assertThat(r.getClosedAt()).isNull();
    }

    // ------------------------------------------------------------------ assignment (API-08)

    @Test
    @DisplayName("Staff accept a New request themselves; it becomes Assigned (FR-008)")
    void staffAcceptsNewRequest() {
        loggedInAs(staff(3));
        ServiceRequest r = existing(10, Status.NEW);

        service.assign(10L, null);

        ArgumentCaptor<RequestHandler> saved = ArgumentCaptor.forClass(RequestHandler.class);
        verify(handlerRepository).save(saved.capture());
        assertThat(saved.getValue().getEmployee().getEmpId()).isEqualTo(3L);
        assertThat(r.getStatus()).isEqualTo(Status.ASSIGNED);
    }

    @Test
    @DisplayName("A request that already has a handler cannot be accepted again")
    void cannotAcceptAssignedRequest() {
        loggedInAs(staff(3));
        ServiceRequest r = existing(10, Status.ASSIGNED);
        activeHandler(r, 2);

        assertApiError(ErrorCode.ALREADY_ASSIGNED, () -> service.assign(10L, null));
        verify(handlerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Staff cannot claim an In Progress request that lost its handler; a manager reassigns it")
    void staffCannotClaimOrphanedInProgress() {
        loggedInAs(staff(3));
        existing(10, Status.IN_PROGRESS); // no active handler: the previous one was removed

        assertApiError(ErrorCode.INVALID_TRANSITION, () -> service.assign(10L, null));
        verify(handlerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Staff cannot assign a request to someone else")
    void staffCannotAssignOthers() {
        loggedInAs(staff(3));
        existing(10, Status.NEW);

        assertApiError(ErrorCode.FORBIDDEN, () -> service.assign(10L, new AssignmentRequest(2L)));
    }

    @Test
    @DisplayName("A manager cannot assign a handler who does not cover the category")
    void managerCannotAssignOutOfScopeHandler() {
        loggedInAs(manager(1));
        existing(10, Status.NEW);
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(employee(4, Employee.Role.STAFF, true)));

        assertApiError(ErrorCode.OUT_OF_SCOPE, () -> service.assign(10L, new AssignmentRequest(4L)));
    }

    @Test
    @DisplayName("A manager cannot assign an inactive employee")
    void managerCannotAssignInactiveEmployee() {
        loggedInAs(manager(1));
        existing(10, Status.NEW);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee(5, Employee.Role.STAFF, false)));

        ApiException ex = assertApiError(ErrorCode.VALIDATION_FAILED,
                () -> service.assign(10L, new AssignmentRequest(5L)));
        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("employeeId");
    }

    @Test
    @DisplayName("Reassigning closes the old handler row first and keeps the current status")
    void managerReassigns() {
        loggedInAs(manager(1));
        ServiceRequest r = existing(10, Status.IN_PROGRESS);
        RequestHandler old = new RequestHandler(r, employee(5, Employee.Role.STAFF, true));
        when(handlerRepository.findActive(10L)).thenReturn(Optional.of(old));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employee(3, Employee.Role.STAFF, true)));

        service.assign(10L, new AssignmentRequest(3L));

        assertThat(old.getUnassignedAt()).isNotNull();
        verify(handlerRepository).saveAndFlush(old); // flushed before the new active row is inserted
        verify(handlerRepository).save(argThat(rh -> rh.getEmployee().getEmpId().equals(3L)));
        assertThat(r.getStatus()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    @DisplayName("Closed requests cannot be assigned")
    void cannotAssignClosedRequest() {
        loggedInAs(manager(1));
        existing(10, Status.REJECTED);

        assertApiError(ErrorCode.REQUEST_CLOSED, () -> service.assign(10L, new AssignmentRequest(3L)));
    }

    // ------------------------------------------------------------------ priority (API-12)

    @Test
    @DisplayName("A manager sets the priority; it is returned and updatedAt moves (FR-016)")
    void managerSetsPriority() {
        loggedInAs(manager(1));
        ServiceRequest r = existing(10, Status.ASSIGNED);
        r.setUpdatedAt(null);

        RequestDetail detail = service.setPriority(10L, Priority.HIGH);

        assertThat(detail.priority()).isEqualTo(Priority.HIGH);
        assertThat(r.getUpdatedAt()).isNotNull();
        verify(requestRepository).findByIdForUpdate(10L); // the row is locked before it changes
    }

    @Test
    @DisplayName("Closed requests keep their priority (FR-011)")
    void priorityOnClosedRequestRefused() {
        loggedInAs(manager(1));
        existing(10, Status.CLOSED);

        assertApiError(ErrorCode.REQUEST_CLOSED, () -> service.setPriority(10L, Priority.LOW));
    }

    @Test
    @DisplayName("Status changes lock the request row first, so concurrent changes are applied one at a time")
    void statusChangeLocksRow() {
        loggedInAs(manager(1));
        existing(10, Status.NEW);

        service.changeStatus(10L, new StatusChangeRequest(Status.REJECTED, null, null));

        verify(requestRepository).findByIdForUpdate(10L);
        verify(requestRepository, never()).findById(10L);
    }

    @Test
    @DisplayName("Staff see the handler's name in the request detail; requesters do not")
    void handlerNameShownToStaffOnly() {
        ServiceRequest r = existing(10, Status.ASSIGNED);
        activeHandler(r, 3);
        EmployeeRepository.EmployeeName name = mock(EmployeeRepository.EmployeeName.class);
        when(name.getId()).thenReturn(3L);
        when(name.fullName()).thenReturn("Ayesha Patel");
        when(employeeRepository.findNames(List.of(3L))).thenReturn(List.of(name));

        loggedInAs(staff(3));
        assertThat(service.get(10L).handlerName()).isEqualTo("Ayesha Patel");

        loggedInAs(requester(1));
        RequestDetail seenByRequester = service.get(10L);
        assertThat(seenByRequester.handlerName()).isNull();
        assertThat(seenByRequester.handlerId()).isNull();
    }

    @Test
    @DisplayName("Manager list applies every filter and shows each request's handler name")
    @SuppressWarnings("unchecked")
    void managerListAppliesFiltersAndNames() {
        loggedInAs(manager(1));
        ServiceRequest a = request(10, Status.ASSIGNED, itSupport, 1);
        ServiceRequest b = request(11, Status.NEW, itSupport, 2);
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b)));
        when(handlerRepository.findActiveHandlerIds(List.of(10L, 11L))).thenReturn(List.<Object[]>of(new Object[] {10L, 3L}));
        EmployeeRepository.EmployeeName name = mock(EmployeeRepository.EmployeeName.class);
        when(name.getId()).thenReturn(3L);
        when(name.fullName()).thenReturn("Ayesha Patel");
        when(employeeRepository.findNames(List.of(3L))).thenReturn(List.of(name));

        Page<RequestSummary> page = service.list(new RequestFilter(Status.ASSIGNED, false, false, 4L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(RequestSummary::handlerName).containsExactly("Ayesha Patel", null);
        ArgumentCaptor<Specification<ServiceRequest>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(requestRepository).findAll(spec.capture(), any(Pageable.class));
        Root<ServiceRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        spec.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(root.get("status"), Status.ASSIGNED);
        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(LocalDate.of(2026, 10, 1).atStartOfDay()));
        verify(cb).lessThan(any(Expression.class), eq(LocalDate.of(2026, 11, 1).atStartOfDay()));
    }

    @Test
    @DisplayName("Staff without categories get an empty list without a query; staff with categories are scoped")
    @SuppressWarnings("unchecked")
    void staffListIsScoped() {
        loggedInAs(staff(9));
        assertThat(service.list(RequestFilter.none(), PageRequest.of(0, 20))).isEmpty();
        verify(requestRepository, never()).findAll(any(Specification.class), any(Pageable.class));

        loggedInAs(staff(3));
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.list(RequestFilter.none(), PageRequest.of(0, 20));
        ArgumentCaptor<Specification<ServiceRequest>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(requestRepository).findAll(spec.capture(), any(Pageable.class));
        Root<ServiceRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        spec.getValue().toPredicate(root, mock(CriteriaQuery.class), mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS));
        verify(root.get("category").get("catId")).in(List.of(2L, 4L));
        verify(handlerRepository, never()).findActiveHandlerIds(any());
    }

    @Test
    @DisplayName("A requester's own list has no handler details")
    @SuppressWarnings("unchecked")
    void mineHasNoHandler() {
        loggedInAs(requester(1));
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request(10, Status.NEW, itSupport, 1))));

        assertThat(service.mine(PageRequest.of(0, 20)).getContent().get(0).handlerId()).isNull();
    }

    @Test
    @DisplayName("Manager assignment errors: no employee given, unknown employee, a manager, the same handler again")
    void managerAssignmentErrors() {
        loggedInAs(manager(1));
        ServiceRequest r = existing(10, Status.ASSIGNED);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1, Employee.Role.MANAGEMENT, true)));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employee(3, Employee.Role.STAFF, true)));
        activeHandler(r, 3);

        assertApiError(ErrorCode.VALIDATION_FAILED, () -> service.assign(10L, null));
        assertApiError(ErrorCode.VALIDATION_FAILED, () -> service.assign(10L, new AssignmentRequest(null)));
        assertApiError(ErrorCode.VALIDATION_FAILED, () -> service.assign(10L, new AssignmentRequest(77L)));
        assertApiError(ErrorCode.VALIDATION_FAILED, () -> service.assign(10L, new AssignmentRequest(1L)));
        assertApiError(ErrorCode.ALREADY_ASSIGNED, () -> service.assign(10L, new AssignmentRequest(3L)));
    }

    @Test
    @DisplayName("Resolved requests cannot be reassigned; a lost race on the database index is ALREADY_ASSIGNED")
    void assignmentEdgeCases() {
        loggedInAs(manager(1));
        existing(10, Status.RESOLVED);
        assertApiError(ErrorCode.INVALID_TRANSITION, () -> service.assign(10L, new AssignmentRequest(3L)));

        loggedInAs(staff(3));
        existing(11, Status.NEW);
        doThrow(new DataIntegrityViolationException("ux_request_handler_active")).when(handlerRepository).flush();
        assertApiError(ErrorCode.ALREADY_ASSIGNED, () -> service.assign(11L, new AssignmentRequest(3L)));
    }

    @Test
    @DisplayName("Rejecting sets closedAt; the same priority again changes nothing")
    void rejectAndUnchangedPriority() {
        loggedInAs(manager(1));
        ServiceRequest r = existing(10, Status.NEW);
        service.changeStatus(10L, new StatusChangeRequest(Status.REJECTED, "  ", null));
        assertThat(r.getClosedAt()).isNotNull();
        verify(commentRepository, never()).save(any());

        ServiceRequest p = existing(12, Status.NEW);
        p.setPriority(Priority.LOW);
        p.setUpdatedAt(null);
        service.setPriority(12L, Priority.LOW);
        assertThat(p.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Staff may reject a New request in their categories without being its handler")
    void staffRejectsNew() {
        loggedInAs(staff(3));
        existing(10, Status.NEW);

        assertThat(service.changeStatus(10L, new StatusChangeRequest(Status.REJECTED, null, null)).status())
                .isEqualTo(Status.REJECTED);
    }

    @Test
    @DisplayName("open=true and unassigned=true add their filters")
    @SuppressWarnings("unchecked")
    void openAndUnassignedFilters() {
        loggedInAs(manager(1));
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(new RequestFilter(null, true, true, null, null, null), PageRequest.of(0, 20));

        ArgumentCaptor<Specification<ServiceRequest>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(requestRepository).findAll(spec.capture(), any(Pageable.class));
        Root<ServiceRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
        spec.getValue().toPredicate(root, query, cb);
        verify(root.get("status")).in(ServiceRequest.OPEN_STATUSES);
        verify(cb).exists(any());
    }

    @Test
    @DisplayName("Staff who are not the handler are offered no status changes; the handler and managers are")
    void allowedTransitionsDependOnTheUser() {
        ServiceRequest r = existing(10, Status.ASSIGNED);
        activeHandler(r, 3);

        loggedInAs(staff(3));
        assertThat(service.get(10L).allowedTransitions()).containsExactlyInAnyOrder(Status.IN_PROGRESS, Status.REJECTED);
        loggedInAs(staff(5));
        when(scopeRepository.findCategoryIds(5L)).thenReturn(List.of(4L));
        assertThat(service.get(10L).allowedTransitions()).isEmpty();
        loggedInAs(manager(1));
        assertThat(service.get(10L).allowedTransitions()).containsExactlyInAnyOrder(Status.IN_PROGRESS, Status.REJECTED);
    }

    @Test
    @DisplayName("The detail carries the status history from the audit trail, oldest first, as UTC instants")
    void statusHistory() {
        existing(10, Status.ASSIGNED);
        when(requestRepository.statusHistory(10L)).thenReturn(List.of(
                new Object[] {"NEW", java.sql.Timestamp.valueOf("2026-10-01 08:00:00")},
                new Object[] {"ASSIGNED", java.time.LocalDateTime.of(2026, 10, 2, 9, 30)}));
        loggedInAs(requester(1));

        assertThat(service.get(10L).statusHistory()).containsExactly(
                new com.civicconnect.requests.dto.StatusChange(Status.NEW, java.time.Instant.parse("2026-10-01T08:00:00Z")),
                new com.civicconnect.requests.dto.StatusChange(Status.ASSIGNED, java.time.Instant.parse("2026-10-02T09:30:00Z")));
    }
}
