package com.civicconnect.access.event;

/**
 * Published when an employee stops being an active staff member (deactivated, or moved to Management).
 * The Requests module listens and takes them off their open requests, inside the same transaction,
 * so the employee change and the unassignments commit or roll back together (DEC-006, DEC-012).
 *
 * @param empId   the employee who is no longer a handler
 * @param actorId the manager who made the change (author of the note left on each request)
 * @param reason  short text for that note, e.g. "deactivated"
 */
public record StaffMemberRemovedEvent(Long empId, Long actorId, String reason) {}
