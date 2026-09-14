package com.nilm.monitoring.domain;

import java.time.Instant;

final class DomainChecks {

    private DomainChecks() {
    }

    static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    static String text(String value, String name, int maxLength) {
        required(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    static void chronological(Instant previous, Instant next, String name) {
        required(next, name);
        if (previous != null && next.isBefore(previous)) {
            throw new IllegalArgumentException(name + " must not be before the previous change");
        }
    }
}
