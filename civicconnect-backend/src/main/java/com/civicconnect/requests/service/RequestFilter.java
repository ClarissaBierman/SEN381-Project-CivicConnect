package com.civicconnect.requests.service;

import com.civicconnect.requests.domain.ServiceRequest.Status;

import java.time.LocalDate;

/**
 * API-07 filters. All optional; open = New, Assigned or In Progress; unassigned = no current handler;
 * from/to are UTC days of the creation date.
 */
public record RequestFilter(Status status, boolean open, boolean unassigned, Long categoryId,
                            LocalDate from, LocalDate to) {

    public static RequestFilter none() {
        return new RequestFilter(null, false, false, null, null, null);
    }
}
