package com.nilm.device.service;

import com.nilm.device.domain.Device;
import com.nilm.device.repository.DeviceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 접속 상태를 DB에 반영한다.
 *
 * <p><b>리스너와 분리한 이유</b>: MQTT 콜백이 같은 객체의 메서드를 직접 호출하면
 * Spring 프록시를 거치지 않아 {@code @Transactional}이 적용되지 않는다.
 * 그러면 변경이 영속화되지 않고 로그만 남는다 — 실제로 그렇게 동작하는 것을 확인했다.
 */
@Service
public class DeviceConnectionUpdater {

    private static final Logger log = LoggerFactory.getLogger(DeviceConnectionUpdater.class);

    private final DeviceRepository deviceRepository;

    public DeviceConnectionUpdater(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public void apply(long deviceId, Device.ConnectionStatus status) {
        deviceRepository.findById(deviceId).ifPresentOrElse(
                device -> {
                    if (device.updateConnection(status, OffsetDateTime.now(ZoneOffset.UTC))) {
                        log.info("기기 접속 상태 변경: deviceId={}, {}", deviceId, status);
                    }
                },
                () -> log.warn("등록되지 않은 기기의 상태 통지: deviceId={}", deviceId));
    }
}
