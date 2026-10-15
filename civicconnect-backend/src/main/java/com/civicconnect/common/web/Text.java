package com.civicconnect.common.web;

/** Small text helpers for request bodies. */
public final class Text {

    private Text() {}

    /** Trims the value; blank becomes null (e.g. an emptied optional phone number). */
    public static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
