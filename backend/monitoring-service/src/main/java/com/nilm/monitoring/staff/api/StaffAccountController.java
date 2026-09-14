package com.nilm.monitoring.staff.api;

import com.nilm.monitoring.security.CurrentUserService;
import com.nilm.monitoring.staff.dto.NotificationSettingsResponse;
import com.nilm.monitoring.staff.dto.StaffProfileResponse;
import com.nilm.monitoring.staff.dto.UpdateNotificationSettingsRequest;
import com.nilm.monitoring.staff.dto.UpdateStaffProfileRequest;
import com.nilm.monitoring.staff.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/me")
public class StaffAccountController {
    private final StaffAccountService service;
    private final CurrentUserService currentUser;

    public StaffAccountController(StaffAccountService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<StaffProfileResponse> me() {
        return noStore(service.getProfile(currentUser.userId(), currentUser.email()));
    }

    @PatchMapping
    public ResponseEntity<StaffProfileResponse> update(@Valid @RequestBody UpdateStaffProfileRequest request) {
        return noStore(service.updateProfile(currentUser.userId(), currentUser.email(), request));
    }

    @GetMapping("/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> notificationSettings() {
        return noStore(service.getNotificationSettings(currentUser.userId()));
    }

    @PutMapping("/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return noStore(service.updateNotificationSettings(currentUser.userId(), request));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
