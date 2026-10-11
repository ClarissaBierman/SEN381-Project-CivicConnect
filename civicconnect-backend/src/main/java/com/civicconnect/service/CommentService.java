package com.civicconnect.service;

import com.civicconnect.error.ErrorCode;
import com.civicconnect.dto.CommentResponse;
import com.civicconnect.dto.NewComment;
import com.civicconnect.model.Comment;
import com.civicconnect.model.Employee;
import com.civicconnect.model.ServiceRequest;
import com.civicconnect.repository.CommentRepository;
import com.civicconnect.security.CurrentUser;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Comments and resolution notes (FR-010). Append-only: there is no edit or delete. */
@Service
public class CommentService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CommentRepository commentRepository;
    private final RequestAccess access;
    private final TransitionPolicy transitionPolicy;
    private final CurrentUserService currentUser;
    private final DbSession db;

    public CommentService(CommentRepository commentRepository, RequestAccess access, TransitionPolicy transitionPolicy,
                          CurrentUserService currentUser, DbSession db) {
        this.commentRepository = commentRepository;
        this.access = access;
        this.transitionPolicy = transitionPolicy;
        this.currentUser = currentUser;
        this.db = db;
    }

    /** API-10: staff (in scope) or a manager adds a comment. Closed and rejected requests are read-only. */
    @Transactional
    public CommentResponse add(Long requestId, NewComment body) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        ServiceRequest request = access.load(requestId);
        access.checkStaffScope(request, user);
        if (transitionPolicy.isTerminal(request.getStatus())) {
            throw Errors.rule(ErrorCode.REQUEST_CLOSED, "Closed or rejected requests are read-only.");
        }

        Comment comment = commentRepository.save(new Comment(request,
                entityManager.getReference(Employee.class, user.id()),
                body.content().trim(), Boolean.TRUE.equals(body.visibleToRequester())));
        return CommentResponse.from(comment);
    }

    /** Comments on a request. Requesters only get the ones marked visible to them (FR-002). */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long requestId) {
        CurrentUser user = currentUser.get();
        db.begin(user);

        access.loadViewable(requestId, user);
        List<Comment> comments = user.isRequester()
                ? commentRepository.findVisibleForRequest(requestId)
                : commentRepository.findAllForRequest(requestId);
        return comments.stream().map(CommentResponse::from).toList();
    }
}
