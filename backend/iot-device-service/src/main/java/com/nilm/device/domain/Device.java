package com.nilm.device.domain;

import com.nilm.device.common.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "devices")
public class Device {

    /** 허용된 상태 전이 — 이 맵에 없는 전이는 전부 거부 (RETIRED는 종착 상태) */
    private static final Map<DeviceStatus, Set<DeviceStatus>> ALLOWED_TRANSITIONS = Map.of(
            DeviceStatus.REGISTERED, Set.of(DeviceStatus.ACTIVE, DeviceStatus.RETIRED),
            DeviceStatus.ACTIVE, Set.of(DeviceStatus.SUSPENDED, DeviceStatus.RETIRED),
            DeviceStatus.SUSPENDED, Set.of(DeviceStatus.ACTIVE, DeviceStatus.RETIRED),
            DeviceStatus.RETIRED, Set.of()
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "house_id", nullable = false, length = 10)
    private String houseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    private DeviceType deviceType;

    @Column(length = 50)
    private String location;

    @Column(name = "firmware_ver", length = 20)
    private String firmwareVer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DeviceStatus status = DeviceStatus.REGISTERED;

    @Column(name = "registered_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime registeredAt;

    protected Device() {
    }

    public Device(String houseId, DeviceType deviceType, String location, String firmwareVer) {
        this.houseId = houseId;
        this.deviceType = deviceType;
        this.location = location;
        this.firmwareVer = firmwareVer;
    }

    /** 상태 전이 — 허용 목록에 없으면 예외. 호출자는 install_history 기록 책임을 가진다. */
    public DeviceStatus transitionTo(DeviceStatus target) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        DeviceStatus from = this.status;
        this.status = target;
        return from;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getHouseId() {
        return houseId;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getLocation() {
        return location;
    }

    public String getFirmwareVer() {
        return firmwareVer;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }
}
