package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.RequestHandler;
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

    /** Every open assignment of one employee (used when they stop being a handler). */
    @Query("select rh from RequestHandler rh join fetch rh.request where rh.employee.empId = :empId and rh.unassignedAt is null")
    List<RequestHandler> findActiveForEmployee(@Param("empId") Long empId);
}
