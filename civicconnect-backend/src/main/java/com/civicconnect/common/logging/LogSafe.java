package com.civicconnect.common.logging;

/**
 * Makes a value that came from a request safe to write into a log line (CodeQL "log injection", D-37).
 * Line breaks and other control characters are replaced, so a value can never start a fake log line, and
 * very long values are cut short so one request cannot flood the log.
 */
public final class LogSafe {

    static final int MAX_LENGTH = 300;

    private LogSafe() {}

    public static String of(Object value) {
        if (value == null) {
            return "null";
        }
        String text = value.toString();
        StringBuilder safe = new StringBuilder(Math.min(text.length(), MAX_LENGTH + 3));
        for (int i = 0; i < text.length() && safe.length() < MAX_LENGTH; i++) {
            char c = text.charAt(i);
            safe.append(Character.isISOControl(c) || c == ' ' || c == ' ' ? '_' : c);
        }
        if (text.length() > MAX_LENGTH) {
            safe.append("...");
        }
        return safe.toString();
    }
}
