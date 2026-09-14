package com.nilm.monitoring.staff.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.common.ConflictException;
import com.nilm.monitoring.common.ForbiddenException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.domain.StaffProfile;
import com.nilm.monitoring.domain.StaffSetting;
import com.nilm.monitoring.staff.dto.NotificationSettingsResponse;
import com.nilm.monitoring.staff.dto.StaffProfileResponse;
import com.nilm.monitoring.staff.dto.UpdateNotificationSettingsRequest;
import com.nilm.monitoring.staff.dto.UpdateStaffProfileRequest;
import com.nilm.monitoring.staff.repository.StaffProfileRepository;
import com.nilm.monitoring.staff.repository.StaffSettingRepository;
import com.nilm.monitoring.outbox.service.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccountService {
    private final StaffProfileRepository profiles;
    private final StaffSettingRepository settings;
    private final Clock clock;
    private final OutboxWriter outbox;
    private final ObjectMapper mapper;

    public StaffAccountService(StaffProfileRepository profiles, StaffSettingRepository settings, Clock clock,
                               OutboxWriter outbox, ObjectMapper mapper) {
        this.profiles = profiles;
        this.settings = settings;
        this.clock = clock;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public StaffProfile requireActive(String authSub) {
        StaffProfile profile = profiles.findByAuthSub(authSub)
                .orElseThrow(() -> new ResourceNotFoundException("담당자 프로필을 찾을 수 없습니다."));
        if (!profile.isActive()) throw new ForbiddenException("비활성 담당자는 이 기능을 사용할 수 없습니다.");
        return profile;
    }

    @Transactional(readOnly = true)
    public String findAuthSub(Long staffId) {
        return profiles.findById(staffId).map(StaffProfile::getAuthSub)
                .orElseThrow(() -> new ResourceNotFoundException("담당자 프로필을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public StaffProfileResponse getProfile(String authSub, String email) {
        return StaffProfileResponse.from(requireActive(authSub), email, Instant.now(clock));
    }

    @Transactional
    public StaffProfileResponse updateProfile(String authSub, String email, UpdateStaffProfileRequest request) {
        StaffProfile profile = requireActive(authSub);
        requireRevision(request.expectedRevision(), profile.getRevision());
        profile.updateProfile(request.displayName(), request.organizationName(), Instant.now(clock));
        profiles.flush();
        outbox.writePersonal(authSub, "settings-updated", mapper.createObjectNode()
                .put("section", "PROFILE").put("revision", profile.getRevision() + 1));
        return StaffProfileResponse.from(profile, email, Instant.now(clock));
    }

    @Transactional
    public NotificationSettingsResponse getNotificationSettings(String authSub) {
        StaffProfile profile = requireActive(authSub);
        StaffSetting setting = settings.findByStaffId(profile.getId())
                .orElseGet(() -> settings.saveAndFlush(new StaffSetting(profile.getId(), Instant.now(clock))));
        return NotificationSettingsResponse.from(setting, Instant.now(clock));
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            String authSub, UpdateNotificationSettingsRequest request) {
        StaffProfile profile = requireActive(authSub);
        StaffSetting setting = settings.findByStaffIdForUpdate(profile.getId())
                .orElseGet(() -> settings.saveAndFlush(new StaffSetting(profile.getId(), Instant.now(clock))));
        requireRevision(request.expectedRevision(), setting.getRevision());
        if (Boolean.TRUE.equals(request.dailySummaryEnabled())) {
            throw new ConflictException("일일 요약 발송 기능은 아직 활성화되지 않았습니다.");
        }
        setting.updateNotifications(request.dangerEnabled(), request.warningEnabled(), false,
                request.soundEnabled(), Instant.now(clock));
        settings.flush();
        outbox.writePersonal(authSub, "settings-updated", mapper.createObjectNode()
                .put("section", "NOTIFICATION").put("revision", setting.getRevision() + 1));
        return NotificationSettingsResponse.from(setting, Instant.now(clock));
    }

    public static void requireRevision(String expected, Long storedVersion) {
        long expectedValue;
        try { expectedValue = Long.parseLong(expected); }
        catch (NumberFormatException e) { throw new ConflictException("수정 버전이 올바르지 않습니다."); }
        if (expectedValue != storedVersion + 1) throw new ConflictException("다른 요청에서 먼저 수정되었습니다.");
    }
}
