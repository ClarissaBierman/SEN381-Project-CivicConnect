package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.ServiceRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<ServiceRequest, Long>, JpaSpecificationExecutor<ServiceRequest> {

    /** Status changes of one request, oldest first: [status, changed_at] rows (V5 function). */
    @Query(value = "select h.status, h.changed_at from request_status_history(:reqId) h", nativeQuery = true)
    List<Object[]> statusHistory(@Param("reqId") Long reqId);

    /**
     * Loads a request and locks its row until the transaction ends (SELECT ... FOR UPDATE).
     * Every change to a request goes through this, so two people changing the same request at the same
     * moment are handled one after the other: the second sees the first one's result and the
     * state-machine check runs against it. This prevents lost updates without a version column (DEC-006).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.reqId = :id")
    Optional<ServiceRequest> findByIdForUpdate(@Param("id") Long id);

    /** Next number for the reference number (CC-2026-000013). */
    @Query(value = "select nextval('request_ref_seq')", nativeQuery = true)
    long nextRefNumber();
}
