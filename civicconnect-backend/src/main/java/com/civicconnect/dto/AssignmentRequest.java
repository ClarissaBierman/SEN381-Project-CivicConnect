package com.civicconnect.dto;

/**
 * API-08 body. Staff leave employeeId empty to accept the request themselves;
 * managers send the employeeId of the handler to assign.
 */
public record AssignmentRequest(Long employeeId) {}
