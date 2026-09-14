package com.nilm.monitoring.subject.api;

import com.nilm.monitoring.common.dto.CursorPage;
import com.nilm.monitoring.security.CurrentUserService;
import com.nilm.monitoring.subject.dto.SubjectCandidateResponse;
import com.nilm.monitoring.subject.dto.SubjectCandidateSearchRequest;
import com.nilm.monitoring.subject.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/subject-candidates")
public class SubjectCandidateController {
    private final AssignmentService service;
    private final CurrentUserService currentUser;
    public SubjectCandidateController(AssignmentService service, CurrentUserService currentUser) {
        this.service = service; this.currentUser = currentUser;
    }
    @PostMapping("/search")
    public ResponseEntity<CursorPage<SubjectCandidateResponse>> search(
            @Valid @RequestBody SubjectCandidateSearchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.candidates(currentUser.userId(), request));
    }
}
