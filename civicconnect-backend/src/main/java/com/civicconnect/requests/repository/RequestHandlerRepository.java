package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.RequestHandler;
import com.civicconnect.requests.domain.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequestHandlerRepository extends JpaRepository<RequestHandler, Long> {

    /** The current handler of a request (the row that has not been unassigned). */
    @Query("select rh from RequestHandler rh where rh.request.reqId = :reqId and rh.unassignedAt is null")
    Optional<RequestHandler> findActive(@Param("reqId") Long reqId);

    /** Current handler ID for each of several requests, for list pages: [reqId, empId] rows. */
    @Query("select rh.request.reqId, rh.employee.empId from RequestHandler rh "
            + "where rh.request.reqId in :reqIds and rh.unassignedAt is null")
    List<Object[]> findActiveHandlerIds(@Param("reqIds") Collection<Long> reqIds);

    /**
     * The employee's current assignments on requests that are still being worked on (New, Assigned,
     * In Progress), used when they stop being a handler. Resolved, Closed and Rejected requests keep
     * their handler on record and are never changed (FR-011).
     */
    @Query("select rh from RequestHandler rh join fetch rh.request r where rh.employee.empId = :empId "
            + "and rh.unassignedAt is null and r.status in :statuses")
    List<RequestHandler> findActiveForEmployee(@Param("empId") Long empId,
                                               @Param("statuses") Collection<ServiceRequest.Status> statuses);
}
