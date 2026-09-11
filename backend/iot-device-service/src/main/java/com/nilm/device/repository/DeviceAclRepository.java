package com.nilm.device.repository;

import com.nilm.device.domain.DeviceAcl;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceAclRepository extends JpaRepository<DeviceAcl, Long> {

    List<DeviceAcl> findByDeviceId(Long deviceId);
}
