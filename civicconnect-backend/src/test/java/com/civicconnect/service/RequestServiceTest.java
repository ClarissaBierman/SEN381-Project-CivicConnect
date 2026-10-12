package com.civicconnect.service;

import com.civicconnect.dto.AssignmentRequest;
import com.civicconnect.dto.NewServiceRequest;
import com.civicconnect.dto.RequestDetail;
import com.civicconnect.dto.StatusChangeRequest;
import com.civicconnect.error.ApiException;
import com.civicconnect.error.ErrorCode;
import com.civicconnect.model.*;
import com.civicconnect.model.ServiceRequest.Status;
import com.civicconnect.repository.*;
import com.civicconnect.security.CurrentUser;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import jakarta.persistence.EntityManager;
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
    private DbSession db;
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
        db = mock(DbSession.class);

        RequestAccess access = new RequestAccess(requestRepository, scopeRepository);
        service = new RequestService(requestRepository, categoryRepository, employeeRepository, handlerRepository,
                commentRepository, scopeRepository, new TransitionPolicy(), access, currentUser, db);

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
        verify(db).begin(requester(1));
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
}
