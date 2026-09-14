package com.nilm.monitoring.policy.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.domain.RiskPolicy;
import com.nilm.monitoring.domain.RiskPolicyChange;
import com.nilm.monitoring.domain.StaffSetting;
import com.nilm.monitoring.outbox.service.OutboxWriter;
import com.nilm.monitoring.policy.dto.RiskPolicyChangeResult;
import com.nilm.monitoring.policy.repository.RiskPolicyChangeRepository;
import com.nilm.monitoring.policy.repository.RiskPolicyRepository;
import com.nilm.monitoring.staff.repository.StaffSettingRepository;
import com.nilm.monitoring.staff.service.StaffAccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskPolicyServiceTest {
    private RiskPolicyRepository policies;
    private RiskPolicyChangeRepository changes;
    private StaffSettingRepository settings;
    private StaffAccountService staffAccounts;
    private OutboxWriter outbox;
    private RiskPolicyService service;

    @BeforeEach
    void setUp() {
        policies = mock(RiskPolicyRepository.class);
        changes = mock(RiskPolicyChangeRepository.class);
        settings = mock(StaffSettingRepository.class);
        staffAccounts = mock(StaffAccountService.class);
        outbox = mock(OutboxWriter.class);
        service = new RiskPolicyService(policies, changes, settings, staffAccounts,
                Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC),
                outbox, new ObjectMapper());
    }

    @Test
    void appliedResultUpdatesDefaultPolicyAndCompletesChange() {
        UUID id = UUID.randomUUID();
        RiskPolicyChange change = mock(RiskPolicyChange.class);
        RiskPolicy policy = mock(RiskPolicy.class);
        StaffSetting setting = mock(StaffSetting.class);
        when(changes.findByIdForUpdate(id)).thenReturn(Optional.of(change));
        when(change.getStatus()).thenReturn(com.nilm.monitoring.domain.RiskPolicyChangeStatus.PENDING);
        when(change.getStaffId()).thenReturn(10L);
        when(change.getWarningThreshold()).thenReturn((short) 40);
        when(change.getDangerThreshold()).thenReturn((short) 70);
        when(change.getMinDurationSeconds()).thenReturn(1800);
        when(policies.findById(12L)).thenReturn(Optional.of(policy));
        when(policy.getId()).thenReturn(12L);
        when(policy.getWarningThreshold()).thenReturn((short) 40);
        when(policy.getDangerThreshold()).thenReturn((short) 70);
        when(policy.getMinDurationSeconds()).thenReturn(1800);
        when(settings.findByStaffIdForUpdate(10L)).thenReturn(Optional.of(setting));
        when(setting.getRevision()).thenReturn(1L);
        when(staffAccounts.findAuthSub(10L)).thenReturn("staff-sub");

        service.completeChange(new RiskPolicyChangeResult(id, "APPLIED", 12L, null));

        verify(setting).applyDefaultPolicy(12L, Instant.parse("2026-09-14T00:00:00Z"));
        verify(change).markApplied(12L, Instant.parse("2026-09-14T00:00:00Z"));
        verify(outbox).writePersonal(org.mockito.ArgumentMatchers.eq("staff-sub"),
                org.mockito.ArgumentMatchers.eq("settings-updated"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appliedResultMustMatchRequestedPolicy() {
        UUID id = UUID.randomUUID();
        RiskPolicyChange change = mock(RiskPolicyChange.class);
        RiskPolicy policy = mock(RiskPolicy.class);
        when(changes.findByIdForUpdate(id)).thenReturn(Optional.of(change));
        when(change.getStatus()).thenReturn(com.nilm.monitoring.domain.RiskPolicyChangeStatus.PENDING);
        when(change.getWarningThreshold()).thenReturn((short) 40);
        when(change.getDangerThreshold()).thenReturn((short) 70);
        when(change.getMinDurationSeconds()).thenReturn(1800);
        when(policies.findById(12L)).thenReturn(Optional.of(policy));
        when(policy.getWarningThreshold()).thenReturn((short) 30);
        when(policy.getDangerThreshold()).thenReturn((short) 70);
        when(policy.getMinDurationSeconds()).thenReturn(1800);

        assertThatThrownBy(() -> service.completeChange(
                new RiskPolicyChangeResult(id, "APPLIED", 12L, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }
}
