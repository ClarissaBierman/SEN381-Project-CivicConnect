package com.civicconnect.requests.dto;

import com.civicconnect.requests.domain.ServiceRequest;

import java.time.Instant;

/** One status a request has had and when it got it (from the audit trail, no names). */
public record StatusChange(ServiceRequest.Status status, Instant at) {}
