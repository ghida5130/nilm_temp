package com.nilm.monitoring.api;

import com.nilm.monitoring.dto.MyDashboardResponse;
import com.nilm.monitoring.dto.SubjectMonitoringResponse;
import com.nilm.monitoring.service.MyDashboardService;
import com.nilm.monitoring.service.SubjectMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitoring")
public class MonitoringDashboardController {

    private final SubjectMonitoringService subjectMonitoringService;
    private final MyDashboardService myDashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<SubjectMonitoringResponse> dashboard(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String authSub = jwt == null ? null : jwt.getSubject();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(subjectMonitoringService.getSubjects(authSub));
    }

    @GetMapping("/my-dashboard")
    public ResponseEntity<MyDashboardResponse> myDashboard(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String authSub = jwt == null ? null : jwt.getSubject();
        return ResponseEntity.ok(myDashboardService.getMyDashboard(authSub));
    }
}
