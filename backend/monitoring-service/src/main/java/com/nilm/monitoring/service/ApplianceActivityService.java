package com.nilm.monitoring.service;

import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.domain.ApplianceState;
import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.kafka.AnalysisSnapshotMessage;
import com.nilm.monitoring.repository.ApplianceStateRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * analysis.snapshot.v1의 가전 ON/OFF 상태를 직전 상태와 비교해
 * ON→OFF 전환만 골라내 대상자의 마지막 활동으로 남긴다.
 *
 * <p>위험 판정과는 무관한 경로다. 스냅샷은 이상 징후가 없어도 계속 들어오므로
 * 아무 일도 일어나지 않는 동안에도 "마지막 활동"이 채워진다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplianceActivityService {

    private final SubjectRepository subjects;
    private final ApplianceStateRepository applianceStates;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void handle(AnalysisSnapshotMessage message) {
        if (!isProcessable(message)) {
            return;
        }

        // 스냅샷 토픽에는 이 서비스가 모르는 가구도 흘러온다.
        // 컨슈머를 멈추지 않고 조용히 건너뛴다.
        if (!subjects.existsByHouseholdId(message.householdId())) {
            log.debug("등록되지 않은 가구의 스냅샷 무시: householdId={}", message.householdId());
            return;
        }

        List<ApplianceState> stored =
                applianceStates.findByHouseholdId(message.householdId());
        if (isStale(stored, message.observedAt())) {
            log.debug("늦게 도착한 스냅샷 무시: householdId={}, observedAt={}",
                    message.householdId(), message.observedAt());
            return;
        }

        Map<String, ApplianceState> byType = new HashMap<>();
        for (ApplianceState state : stored) {
            byType.put(state.getApplianceType(), state);
        }

        List<String> turnedOff = new ArrayList<>();
        List<ApplianceState> created = new ArrayList<>();
        for (AnalysisSnapshotMessage.Appliance appliance : message.appliances()) {
            if (appliance == null || appliance.isOn() == null
                    || appliance.applianceType() == null
                    || appliance.applianceType().isBlank()) {
                continue;
            }
            ApplianceState previous = byType.get(appliance.applianceType());
            if (previous == null) {
                // 첫 스냅샷은 비교 대상이 없다. 전환으로 보지 않고 상태만 심어 둔다.
                created.add(new ApplianceState(
                        message.householdId(),
                        appliance.applianceType(),
                        appliance.isOn(),
                        message.observedAt()
                ));
                continue;
            }
            if (previous.changeTo(appliance.isOn(), message.observedAt())) {
                turnedOff.add(appliance.applianceType());
            }
        }
        if (!created.isEmpty()) {
            applianceStates.saveAll(created);
        }
        if (turnedOff.isEmpty()) {
            return;
        }

        // 한 스냅샷에서 여러 대가 동시에 꺼지면 시각이 모두 같다.
        // 무엇을 골라도 등가이므로 코드 순으로 정해 결과를 재현 가능하게 둔다.
        String appliance = turnedOff.stream().min(Comparator.naturalOrder()).orElseThrow();

        // 분석 이벤트 소비와 같은 행을 갱신한다. 같은 락을 잡아 덮어쓰기를 막는다.
        List<Subject> matches = subjects.findHouseholdForUpdate(message.householdId());
        if (matches.size() != 1) {
            log.warn("가구에 대상자가 정확히 한 명이 아니어서 마지막 활동을 건너뛴다: householdId={}, 수={}",
                    message.householdId(), matches.size());
            return;
        }
        Subject subject = matches.get(0);
        boolean updated = subject.recordActivity(
                appliance,
                message.observedAt(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        if (updated) {
            publisher.publishEvent(
                    new SubjectStateChanged(subject.getId(), StateChangeTrigger.ACTIVITY));
        }
    }

    private boolean isProcessable(AnalysisSnapshotMessage message) {
        if (message == null || message.householdId() == null
                || message.householdId().isBlank()
                || message.observedAt() == null
                || message.appliances() == null
                || message.appliances().isEmpty()) {
            log.warn("가전 상태를 읽을 수 없는 스냅샷 무시: snapshotId={}",
                    message == null ? null : message.snapshotId());
            return false;
        }
        return true;
    }

    /**
     * 마지막으로 반영한 전환보다 앞선 스냅샷이면 건너뛴다.
     * 파티션 재배치나 재전송으로 과거 상태가 뒤늦게 덮어쓰는 일을 막는다.
     */
    private boolean isStale(List<ApplianceState> stored, OffsetDateTime observedAt) {
        return stored.stream()
                .map(ApplianceState::getChangedAt)
                .max(Comparator.naturalOrder())
                .filter(observedAt::isBefore)
                .isPresent();
    }
}
