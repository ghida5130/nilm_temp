package com.nilm.monitoring.policy.api;

import com.nilm.monitoring.policy.dto.CreateRiskPolicyChangeRequest;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeAccepted;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeResponse;
import com.nilm.monitoring.policy.dto.RiskPolicyListResponse;
import com.nilm.monitoring.policy.dto.RiskPolicySettingsResponse;
import com.nilm.monitoring.policy.service.RiskPolicyService;
import com.nilm.monitoring.security.CurrentUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
public class RiskPolicyController {
    private final RiskPolicyService service;
    private final CurrentUserService currentUser;
    public RiskPolicyController(RiskPolicyService service, CurrentUserService currentUser) {
        this.service = service; this.currentUser = currentUser;
    }
    @GetMapping("/risk-policies")
    public RiskPolicyListResponse policies() { return service.list(currentUser.userId()); }
    @GetMapping("/me/risk-policy-settings")
    public ResponseEntity<RiskPolicySettingsResponse> settings() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.settings(currentUser.userId()));
    }
    @PostMapping("/risk-policy-changes")
    public ResponseEntity<RiskPolicyChangeAccepted> change(@Valid @RequestBody CreateRiskPolicyChangeRequest request) {
        RiskPolicyChangeAccepted response = service.requestChange(currentUser.userId(), request);
        return ResponseEntity.accepted().location(URI.create("/api/monitoring/risk-policy-changes/" + response.changeId()))
                .body(response);
    }
    @GetMapping("/risk-policy-changes/{changeId}")
    public RiskPolicyChangeResponse change(@PathVariable UUID changeId) {
        return service.change(currentUser.userId(), changeId);
    }
}
