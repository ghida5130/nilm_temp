package com.nilm.monitoring.domain;

public enum Severity {
    WARNING(1),
    DANGER(2);

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    public boolean includes(Severity severity) {
        return severity != null && severity.level >= level;
    }
}
