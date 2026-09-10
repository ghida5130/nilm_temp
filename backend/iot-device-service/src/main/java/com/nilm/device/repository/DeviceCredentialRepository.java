package com.nilm.device.repository;

import com.nilm.device.domain.DeviceCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, Long> {

    boolean existsByMqttUsername(String mqttUsername);
}
