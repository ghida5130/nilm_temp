package com.nilm.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "device_credentials")
public class DeviceCredential {

    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "mqtt_username", nullable = false, unique = true, length = 50)
    private String mqttUsername;

    @Column(name = "secret_hash", nullable = false, length = 200)
    private String secretHash;

    @Column(name = "issued_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected DeviceCredential() {
    }

    public DeviceCredential(Long deviceId, String mqttUsername, String secretHash) {
        this.deviceId = deviceId;
        this.mqttUsername = mqttUsername;
        this.secretHash = secretHash;
    }

    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getRotatedAt() {
        return rotatedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
