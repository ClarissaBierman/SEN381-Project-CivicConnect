package com.civicconnect.service;

import com.civicconnect.model.ServiceRequest.Status;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.civicconnect.model.ServiceRequest.Status.*;

/**
 * DEC-011: the one place that decides which status moves are legal (FR-009, FR-011, ASR-002).
 * Closed and Rejected have no moves, so those requests are read-only.
 */
@Component
public class TransitionPolicy {

    private final Map<Status, EnumSet<Status>> allowed = new EnumMap<>(Status.class);

    public TransitionPolicy() {
        allowed.put(NEW, EnumSet.of(ASSIGNED, REJECTED));
        allowed.put(ASSIGNED, EnumSet.of(IN_PROGRESS, REJECTED));
        allowed.put(IN_PROGRESS, EnumSet.of(RESOLVED));
        allowed.put(RESOLVED, EnumSet.of(CLOSED));
        allowed.put(CLOSED, EnumSet.noneOf(Status.class));
        allowed.put(REJECTED, EnumSet.noneOf(Status.class));
    }

    public boolean canMove(Status from, Status to) {
        return allowed.get(from).contains(to);
    }

    /** A copy of the statuses a request may move to next (returned as allowedTransitions in API-06). */
    public Set<Status> allowedFrom(Status from) {
        return EnumSet.copyOf(allowed.get(from));
    }

    public boolean isTerminal(Status status) {
        return allowed.get(status).isEmpty();
    }
}
