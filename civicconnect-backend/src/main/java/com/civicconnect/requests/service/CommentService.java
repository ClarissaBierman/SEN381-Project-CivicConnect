package com.civicconnect.requests.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.requests.domain.Comment;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.dto.CommentResponse;
import com.civicconnect.requests.dto.NewComment;
import com.civicconnect.requests.repository.CommentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CommentRepository commentRepository;
    private final RequestAccess access;
    private final TransitionPolicy transitionPolicy;
    private final CommentViews views;
    private final CurrentUserService currentUser;

    public CommentService(CommentRepository commentRepository, RequestAccess access, TransitionPolicy transitionPolicy,
                          CommentViews views, CurrentUserService currentUser) {
        this.commentRepository = commentRepository;
        this.access = access;
        this.transitionPolicy = transitionPolicy;
        this.views = views;
        this.currentUser = currentUser;
    }

    @Transactional
    public CommentResponse add(Long requestId, NewComment body) {
        CurrentUser user = currentUser.get();

        ServiceRequest request = access.loadForUpdate(requestId); // same row lock as status changes
        access.checkStaffScope(request, user);
        if (transitionPolicy.isTerminal(request.getStatus())) {
            throw Errors.rule(ErrorCode.REQUEST_CLOSED, "Closed or rejected requests are read-only.");
        }

        Comment comment = commentRepository.save(new Comment(request,
                entityManager.getReference(Employee.class, user.id()),
                body.content().trim(), Boolean.TRUE.equals(body.visibleToRequester())));
        return views.withNames(List.of(comment)).get(0);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long requestId) {
        CurrentUser user = currentUser.get();
        access.loadViewable(requestId, user);
        return views.forRequest(requestId, user);
    }
}
