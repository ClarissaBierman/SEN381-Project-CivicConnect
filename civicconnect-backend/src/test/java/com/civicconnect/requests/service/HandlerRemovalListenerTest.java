package com.civicconnect.requests.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.event.StaffMemberRemovedEvent;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.domain.Comment;
import com.civicconnect.requests.domain.RequestHandler;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import com.civicconnect.requests.repository.CommentRepository;
import com.civicconnect.requests.repository.RequestHandlerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** A deactivated staff member is taken off their open requests (state transition per current status). */
class HandlerRemovalListenerTest {

    private final RequestHandlerRepository handlers = mock(RequestHandlerRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final HandlerRemovalListener listener = new HandlerRemovalListener(handlers, comments);
    private final Category it = category(4, "IT Support", true);

    HandlerRemovalListenerTest() {
        EntityManager em = mock(EntityManager.class);
        when(em.getReference(eq(Employee.class), any()))
                .thenAnswer(i -> employee((Long) i.getArgument(1), Employee.Role.MANAGEMENT, true));
        ReflectionTestUtils.setField(listener, "entityManager", em);
    }

    @Test
    @DisplayName("Assigned goes back to New; In Progress keeps its status; both assignments are closed with a note")
    void releasesOpenAssignments() {
        ServiceRequest assigned = request(10, Status.ASSIGNED, it, 1);
        ServiceRequest inProgress = request(11, Status.IN_PROGRESS, it, 2);
        RequestHandler a = new RequestHandler(assigned, employee(3, Employee.Role.STAFF, false));
        RequestHandler b = new RequestHandler(inProgress, employee(3, Employee.Role.STAFF, false));
        when(handlers.findActiveForEmployee(3L, ServiceRequest.OPEN_STATUSES)).thenReturn(List.of(a, b));

        listener.onStaffMemberRemoved(new StaffMemberRemovedEvent(3L, 1L, "deactivated"));

        assertThat(assigned.getStatus()).isEqualTo(Status.NEW);
        assertThat(inProgress.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(a.getUnassignedAt()).isNotNull();
        assertThat(b.getUnassignedAt()).isNotNull();

        ArgumentCaptor<Comment> notes = ArgumentCaptor.forClass(Comment.class);
        verify(comments, times(2)).save(notes.capture());
        assertThat(notes.getAllValues()).allSatisfy(n -> {
            assertThat(n.isVisibleToRequester()).as("internal note").isFalse();
            assertThat(n.getEmployee().getEmpId()).as("written by the manager").isEqualTo(1L);
        });
        assertThat(notes.getAllValues().get(0).getContent()).contains("returned to the queue");
        assertThat(notes.getAllValues().get(1).getContent()).contains("reassign");
    }

    @Test
    @DisplayName("A staff member with no open requests: nothing changes")
    void nothingToRelease() {
        when(handlers.findActiveForEmployee(5L, ServiceRequest.OPEN_STATUSES)).thenReturn(List.of());

        listener.onStaffMemberRemoved(new StaffMemberRemovedEvent(5L, 1L, "deactivated"));

        verifyNoInteractions(comments);
    }

    @Test
    @DisplayName("Removed from one category: only requests in that category are released")
    void onlyTheRemovedCategory() {
        ServiceRequest inIt = request(10, Status.ASSIGNED, it, 1);
        ServiceRequest elsewhere = request(11, Status.ASSIGNED, category(2, "Damaged Equipment", true), 1);
        RequestHandler a = new RequestHandler(inIt, employee(3, Employee.Role.STAFF, true));
        RequestHandler b = new RequestHandler(elsewhere, employee(3, Employee.Role.STAFF, true));
        when(handlers.findActiveForEmployee(3L, ServiceRequest.OPEN_STATUSES)).thenReturn(List.of(a, b));

        listener.onStaffMemberRemoved(new StaffMemberRemovedEvent(3L, 1L, "removed from the category", Set.of(4L)));

        assertThat(inIt.getStatus()).isEqualTo(Status.NEW);
        assertThat(a.getUnassignedAt()).isNotNull();
        assertThat(elsewhere.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(b.getUnassignedAt()).isNull();
        verify(comments, times(1)).save(any());
    }
}
