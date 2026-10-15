package com.civicconnect.access.event;

import java.util.Set;

/**
 * Published when an employee stops handling some or all requests: deactivated, moved to Management,
 * or removed from categories. The Requests module listens and takes them off the affected open
 * requests inside the same transaction, so both changes commit or roll back together (DEC-006, DEC-012).
 *
 * @param empId       the employee who is no longer a handler
 * @param actorId     the manager who made the change (author of the note left on each request)
 * @param reason      short text for that note, e.g. "deactivated"
 * @param categoryIds only requests in these categories are affected; null means every category
 */
public record StaffMemberRemovedEvent(Long empId, Long actorId, String reason, Set<Long> categoryIds) {

    /** Affects all of the employee's open requests. */
    public StaffMemberRemovedEvent(Long empId, Long actorId, String reason) {
        this(empId, actorId, reason, null);
    }

    public boolean covers(Long categoryId) {
        return categoryIds == null || categoryIds.contains(categoryId);
    }
}
