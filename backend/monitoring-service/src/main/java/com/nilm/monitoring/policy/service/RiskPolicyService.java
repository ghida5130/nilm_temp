package com.nilm.monitoring.policy.service;

import com.nilm.monitoring.common.BadRequestException;
import com.nilm.monitoring.common.ConflictException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.common.ServiceUnavailableException;
import com.nilm.monitoring.domain.RiskPolicy;
import com.nilm.monitoring.domain.RiskPolicyChange;
import com.nilm.monitoring.domain.RiskPolicyChangeStatus;
import com.nilm.monitoring.domain.StaffProfile;
import com.nilm.monitoring.domain.StaffSetting;
import com.nilm.monitoring.policy.dto.CreateRiskPolicyChangeRequest;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeAccepted;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeResponse;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeResult;
import com.nilm.monitoring.policy.dto.RiskPolicyListResponse;
import com.nilm.monitoring.policy.dto.RiskPolicyResponse;
import com.nilm.monitoring.policy.dto.RiskPolicySettingsResponse;
import com.nilm.monitoring.policy.repository.RiskPolicyChangeRepository;
import com.nilm.monitoring.policy.repository.RiskPolicyRepository;
import com.nilm.monitoring.staff.repository.StaffSettingRepository;
import com.nilm.monitoring.staff.service.StaffAccountService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.outbox.service.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskPolicyService {
    public static final List<Integer> ALLOWED_DURATIONS = List.of(0, 900, 1800, 3600);
    private final RiskPolicyRepository policies;
    private final RiskPolicyChangeRepository changes;
    private final StaffSettingRepository settings;
    private final StaffAccountService staffAccounts;
    private final Clock clock;
    private final OutboxWriter outbox;
    private final ObjectMapper mapper;

    public RiskPolicyService(RiskPolicyRepository policies, RiskPolicyChangeRepository changes,
            StaffSettingRepository settings, StaffAccountService staffAccounts, Clock clock,
            OutboxWriter outbox, ObjectMapper mapper) {
        this.policies = policies; this.changes = changes; this.settings = settings;
        this.staffAccounts = staffAccounts; this.clock = clock; this.outbox = outbox; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public RiskPolicyListResponse list(String authSub) {
        staffAccounts.requireActive(authSub);
        return new RiskPolicyListResponse(policies.findAllByOrderByPolicyCodeAscVersionDesc().stream()
                .map(RiskPolicyResponse::from).toList(), Instant.now(clock));
    }

    @Transactional
    public RiskPolicySettingsResponse settings(String authSub) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        StaffSetting setting = settings.findByStaffIdForUpdate(staff.getId())
                .orElseGet(() -> settings.saveAndFlush(new StaffSetting(staff.getId(), Instant.now(clock))));
        RiskPolicy policy;
        if (setting.getDefaultPolicyId() == null) {
            policy = policies.findAllByOrderByPolicyCodeAscVersionDesc().stream().findFirst()
                    .orElseThrow(() -> new ServiceUnavailableException("선택 가능한 위험 정책이 없습니다."));
            setting.applyDefaultPolicy(policy.getId(), Instant.now(clock));
            settings.flush();
        } else {
            policy = policies.findById(setting.getDefaultPolicyId())
                    .orElseThrow(() -> new ServiceUnavailableException("기본 위험 정책을 찾을 수 없습니다."));
        }
        if (policy.getWarningThreshold() == null || policy.getDangerThreshold() == null) {
            throw new ServiceUnavailableException("점수 임계치가 없는 정책은 기본 설정으로 사용할 수 없습니다.");
        }
        return new RiskPolicySettingsResponse(policy.getId().toString(), policy.getVersion(),
                policy.getWarningThreshold(), policy.getDangerThreshold(), policy.getMinDurationSeconds(),
                ALLOWED_DURATIONS, "FUTURE_ASSIGNMENTS", Long.toString(setting.getRevision() + 1),
                Instant.now(clock));
    }

    @Transactional
    public RiskPolicyChangeAccepted requestChange(String authSub, CreateRiskPolicyChangeRequest request) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        if (request.warningThreshold() >= request.dangerThreshold()) {
            throw new BadRequestException("warningThreshold는 dangerThreshold보다 작아야 합니다.");
        }
        if (!ALLOWED_DURATIONS.contains(request.minDurationSeconds())) {
            throw new BadRequestException("허용되지 않은 지속 시간입니다.");
        }
        StaffSetting setting = settings.findByStaffIdForUpdate(staff.getId())
                .orElseGet(() -> settings.saveAndFlush(new StaffSetting(staff.getId(), Instant.now(clock))));
        StaffAccountService.requireRevision(request.expectedRevision(), setting.getRevision());
        if (changes.existsByStaffIdAndStatus(staff.getId(), RiskPolicyChangeStatus.PENDING)) {
            throw new ConflictException("처리 중인 정책 변경이 있습니다.");
        }
        UUID id = UUID.randomUUID();
        changes.save(new RiskPolicyChange(id, staff.getId(), setting.getRevision() + 1,
                request.warningThreshold(), request.dangerThreshold(), request.minDurationSeconds(),
                request.changeReason(), Instant.now(clock)));
        return new RiskPolicyChangeAccepted(id, "PENDING");
    }

    @Transactional(readOnly = true)
    public RiskPolicyChangeResponse change(String authSub, UUID changeId) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        RiskPolicyChange change = changes.findByIdAndStaffId(changeId, staff.getId())
                .orElseThrow(() -> new ResourceNotFoundException("정책 변경 작업을 찾을 수 없습니다."));
        RiskPolicy policy = change.getPolicyId() == null ? null : policies.findById(change.getPolicyId()).orElse(null);
        return new RiskPolicyChangeResponse(change.getId(), change.getStatus().name(),
                policy == null ? null : policy.getId().toString(), policy == null ? null : policy.getVersion(),
                change.getFailureCode(), Instant.now(clock));
    }

    @Transactional
    public void completeChange(RiskPolicyChangeResult result) {
        RiskPolicyChange change = changes.findByIdForUpdate(result.changeId())
                .orElseThrow(() -> new ResourceNotFoundException("정책 변경 작업을 찾을 수 없습니다."));
        if (change.getStatus() != RiskPolicyChangeStatus.PENDING) return;
        Instant now = Instant.now(clock);
        if ("FAILED".equals(result.status())) {
            String code = result.failureCode() == null || result.failureCode().isBlank()
                    ? "ANALYSIS_REJECTED" : result.failureCode();
            change.markFailed(code, now);
            return;
        }
        if (!"APPLIED".equals(result.status()) || result.policyId() == null) {
            throw new IllegalArgumentException("Risk policy result status/policyId is invalid");
        }
        RiskPolicy policy = policies.findById(result.policyId())
                .orElseThrow(() -> new IllegalStateException("Applied policy is not available in monitoring storage"));
        if (policy.getWarningThreshold() == null || policy.getDangerThreshold() == null
                || policy.getWarningThreshold() != change.getWarningThreshold()
                || policy.getDangerThreshold() != change.getDangerThreshold()
                || policy.getMinDurationSeconds() != change.getMinDurationSeconds()) {
            throw new IllegalStateException("Applied policy does not match the requested thresholds");
        }
        StaffSetting setting = settings.findByStaffIdForUpdate(change.getStaffId())
                .orElseThrow(() -> new IllegalStateException("Staff setting disappeared during policy change"));
        setting.applyDefaultPolicy(policy.getId(), now);
        settings.flush();
        change.markApplied(policy.getId(), now);
        String authSub = staffAccounts.findAuthSub(change.getStaffId());
        outbox.writePersonal(authSub, "settings-updated", mapper.createObjectNode()
                .put("section", "RISK_POLICY").put("revision", setting.getRevision() + 1));
    }
}
