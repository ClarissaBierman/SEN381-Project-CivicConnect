package com.civicconnect.repository;

import com.civicconnect.model.RequestHandler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RequestHandlerRepository extends JpaRepository<RequestHandler, Long> {

    /** The current handler of a request (the row that has not been unassigned). */
    @Query("select rh from RequestHandler rh where rh.request.reqId = :reqId and rh.unassignedAt is null")
    Optional<RequestHandler> findActive(@Param("reqId") Long reqId);
}
