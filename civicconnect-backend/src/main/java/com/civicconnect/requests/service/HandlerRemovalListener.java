package com.civicconnect.requests.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.event.StaffMemberRemovedEvent;
import com.civicconnect.common.time.Time;
import com.civicconnect.requests.domain.Comment;
import com.civicconnect.requests.domain.RequestHandler;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.domain.ServiceRequest.Status;
import com.civicconnect.requests.repository.CommentRepository;
import com.civicconnect.requests.repository.RequestHandlerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * When an employee stops being a handler (deactivated, moved to Management, or removed from a category),
 * take them off the affected open requests (New, Assigned, In Progress) so nothing is left with a handler
 * who can no longer act on it. Resolved, Closed and Rejected requests are not touched (FR-011):
 *  - their assignment row is closed (unassigned_at = now);
 *  - an Assigned request goes back to New, so it reappears in the queue for other handlers to accept;
 *  - an In Progress request keeps its status and waits for a manager to reassign it;
 *  - an internal note (not visible to the requester) records why, written by the manager.
 *
 * A plain @EventListener runs synchronously inside the manager's transaction (DEC-012): if anything here
 * fails, the employee change is rolled back too. Assigned -> New is a system correction, not a user
 * transition, so it does not go through TransitionPolicy; the audit trigger still records it.
 *
 * Each request is locked (SELECT ... FOR UPDATE) and re-read before it is changed, like every other write
 * to a request (C-01). Without the lock, a priority or status change committed by someone else between
 * the first read and this update would be overwritten with the old values (D-20).
 */
@Component
public class HandlerRemovalListener {

    private static final Logger log = LoggerFactory.getLogger(HandlerRemovalListener.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final RequestHandlerRepository handlers;
    private final CommentRepository comments;

    public HandlerRemovalListener(RequestHandlerRepository handlers, CommentRepository comments) {
        this.handlers = handlers;
        this.comments = comments;
    }

    @EventListener
    public void onStaffMemberRemoved(StaffMemberRemovedEvent event) {
        LocalDateTime now = Time.now();
        Employee manager = entityManager.getReference(Employee.class, event.actorId());
        int released = 0;
        for (RequestHandler assignment : handlers.findActiveForEmployee(event.empId(), ServiceRequest.OPEN_STATUSES)) {
            ServiceRequest request = assignment.getRequest();
            if (!event.covers(request.getCategory().getCatId())) {
                continue;
            }
            // Lock the request, then re-read it and its assignment: they may have changed since the query
            entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(assignment);
            if (assignment.getUnassignedAt() != null || !ServiceRequest.OPEN_STATUSES.contains(request.getStatus())) {
                continue;
            }
            assignment.setUnassignedAt(now);

            String next;
            if (request.getStatus() == Status.ASSIGNED) {
                request.setStatus(Status.NEW);
                next = "returned to the queue";
            } else {
                next = "waiting for a manager to reassign it";
            }
            request.setUpdatedAt(now);
            comments.save(new Comment(request, manager,
                    "Handler removed from this request (employee " + event.empId() + " " + event.reason() + "); "
                            + "request " + next + ".", false));
            released++;
        }
        handlers.flush();
        if (released > 0) {
            log.info("Employee {} {}: released from {} open request(s) by manager {}",
                    event.empId(), event.reason(), released, event.actorId());
        }
    }
}
