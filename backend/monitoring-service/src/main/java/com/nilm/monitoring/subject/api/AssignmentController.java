package com.nilm.monitoring.subject.api;

import com.nilm.monitoring.security.CurrentUserService;
import com.nilm.monitoring.subject.dto.AssignmentResponse;
import com.nilm.monitoring.subject.dto.CreateAssignmentRequest;
import com.nilm.monitoring.subject.dto.UpdateAssignmentRequest;
import com.nilm.monitoring.subject.dto.UpdateAssignmentResponse;
import com.nilm.monitoring.subject.service.AssignmentService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/subject-assignments")
public class AssignmentController {
    private final AssignmentService service;
    private final CurrentUserService currentUser;
    public AssignmentController(AssignmentService service, CurrentUserService currentUser) {
        this.service = service; this.currentUser = currentUser;
    }
    @PostMapping
    public ResponseEntity<AssignmentResponse> create(@Valid @RequestBody CreateAssignmentRequest request) {
        AssignmentResponse response = service.create(currentUser.userId(), request);
        return ResponseEntity.created(URI.create("/api/monitoring/subject-assignments/" + response.assignmentId()))
                .body(response);
    }
    @PatchMapping("/{assignmentId}")
    public UpdateAssignmentResponse update(@PathVariable Long assignmentId,
            @Valid @RequestBody UpdateAssignmentRequest request) {
        return service.update(currentUser.userId(), assignmentId, request);
    }
}
