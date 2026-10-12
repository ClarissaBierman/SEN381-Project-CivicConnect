package com.civicconnect.service;

import com.civicconnect.dto.CommentResponse;
import com.civicconnect.dto.NewComment;
import com.civicconnect.error.ErrorCode;
import com.civicconnect.model.Comment;
import com.civicconnect.model.Employee;
import com.civicconnect.model.ServiceRequest;
import com.civicconnect.model.ServiceRequest.Status;
import com.civicconnect.repository.CommentRepository;
import com.civicconnect.repository.HandlerCategoryRepository;
import com.civicconnect.repository.RequestRepository;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static com.civicconnect.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** FR-010 (append-only comments) and FR-002 (requester-visible comments). */
class CommentServiceTest {

    private CommentRepository commentRepository;
    private RequestRepository requestRepository;
    private CurrentUserService currentUser;
    private CommentService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        requestRepository = mock(RequestRepository.class);
        HandlerCategoryRepository scope = mock(HandlerCategoryRepository.class);
        currentUser = mock(CurrentUserService.class);
        when(scope.findCategoryIds(3L)).thenReturn(List.of(4L));

        service = new CommentService(commentRepository, new RequestAccess(requestRepository, scope),
                new TransitionPolicy(), currentUser, mock(DbSession.class));

        EntityManager em = mock(EntityManager.class);
        when(em.getReference(eq(Employee.class), any()))
                .thenAnswer(i -> employee((Long) i.getArgument(1), Employee.Role.STAFF, true));
        ReflectionTestUtils.setField(service, "entityManager", em);
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ServiceRequest existing(Status status) {
        ServiceRequest r = request(10, status, category(4, "IT Support", true), 1);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    @DisplayName("The author is the logged-in employee, and the content is trimmed")
    void addUsesLoggedInAuthor() {
        when(currentUser.get()).thenReturn(staff(3));
        existing(Status.IN_PROGRESS);

        CommentResponse response = service.add(10L, new NewComment("  Waiting for parts  ", null));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getEmployee().getEmpId()).isEqualTo(3L);
        assertThat(saved.getValue().getContent()).isEqualTo("Waiting for parts");
        assertThat(saved.getValue().isVisibleToRequester()).as("internal unless marked visible").isFalse();
        assertThat(response.authorEmpId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Comments cannot be added to a closed request (FR-011)")
    void cannotCommentOnClosedRequest() {
        when(currentUser.get()).thenReturn(staff(3));
        existing(Status.CLOSED);

        assertApiError(ErrorCode.REQUEST_CLOSED, () -> service.add(10L, new NewComment("Too late", null)));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Staff cannot comment outside their categories")
    void staffOutsideCategoryCannotComment() {
        when(currentUser.get()).thenReturn(staff(4));
        existing(Status.IN_PROGRESS);

        assertApiError(ErrorCode.FORBIDDEN, () -> service.add(10L, new NewComment("Hello", null)));
    }

    @Test
    @DisplayName("A requester's list only contains comments visible to them")
    void requesterListIsFiltered() {
        when(currentUser.get()).thenReturn(requester(1));
        ServiceRequest r = existing(Status.IN_PROGRESS);
        Comment visible = new Comment(r, employee(3, Employee.Role.STAFF, true), "Fixed soon", true);
        when(commentRepository.findVisibleForRequest(10L)).thenReturn(List.of(visible));

        assertThat(service.list(10L)).extracting("content").containsExactly("Fixed soon");
        verify(commentRepository, never()).findAllForRequest(any());
    }

    @Test
    @DisplayName("A requester cannot list comments on someone else's request")
    void requesterCannotListOthersComments() {
        when(currentUser.get()).thenReturn(requester(2));
        existing(Status.IN_PROGRESS);

        assertApiError(ErrorCode.NOT_FOUND, () -> service.list(10L));
    }
}
