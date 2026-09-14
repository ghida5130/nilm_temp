package com.nilm.monitoring.subject.api;

import com.nilm.monitoring.security.CurrentUserService;
import com.nilm.monitoring.subject.dto.DashboardSummaryResponse;
import com.nilm.monitoring.subject.service.SubjectQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/dashboard")
public class DashboardController {
    private final SubjectQueryService service;
    private final CurrentUserService currentUser;
    public DashboardController(SubjectQueryService service, CurrentUserService currentUser) {
        this.service = service; this.currentUser = currentUser;
    }
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.dashboard(currentUser.userId()));
    }
}
