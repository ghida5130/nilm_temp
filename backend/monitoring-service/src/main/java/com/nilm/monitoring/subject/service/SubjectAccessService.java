package com.nilm.monitoring.subject.service;

import com.nilm.monitoring.subject.repository.AssignmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectAccessService {
    private final AssignmentRepository assignments;
    public SubjectAccessService(AssignmentRepository assignments) { this.assignments = assignments; }
    @Transactional(readOnly = true)
    public boolean canAccessHousehold(String authSub, String householdId) {
        return assignments.existsActiveAccess(authSub, householdId);
    }
    @Transactional(readOnly = true)
    public List<String> authSubsForHousehold(String householdId) {
        return assignments.findActiveAuthSubsByHouseholdId(householdId);
    }
}
