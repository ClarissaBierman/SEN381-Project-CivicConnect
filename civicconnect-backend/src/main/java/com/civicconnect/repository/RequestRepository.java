package com.civicconnect.repository;

import com.civicconnect.model.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<ServiceRequest, Long>, JpaSpecificationExecutor<ServiceRequest> {

    /** A requester's own requests (FR-003). */
    @Query("select r from ServiceRequest r where r.requester.clientId = :clientId")
    Page<ServiceRequest> findByRequester(@Param("clientId") Long clientId, Pageable pageable);

    /** Next number for the reference number (CC-2026-000013). */
    @Query(value = "select nextval('request_ref_seq')", nativeQuery = true)
    long nextRefNumber();
}
