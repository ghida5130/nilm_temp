package com.nilm.monitoring.subject.dto;

import java.time.Instant;

public record SubjectDetailResponse(String subjectId, String householdId, String subjectNumber,
        String name, int age, String phone, String address, String serviceStatus,
        long totalIncidentCount, long openIncidentCount, Assignment assignment,
        RiskPolicySummary riskPolicy, Instant serverTime) {
    public record Assignment(String id, String memo, String revision) {}
    public record RiskPolicySummary(String id, int version, String name) {}
}
