package com.nilm.monitoring.service;

import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.domain.ApplianceState;
import com.nilm.monitoring.domain.HouseholdDailyApplianceUsage;
import com.nilm.monitoring.domain.HouseholdDailyApplianceUsageId;
import com.nilm.monitoring.domain.HouseholdObservation;
import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.kafka.AnalysisSnapshotMessage;
import com.nilm.monitoring.repository.ApplianceStateRepository;
import com.nilm.monitoring.repository.HouseholdDailyApplianceUsageRepository;
import com.nilm.monitoring.repository.HouseholdObservationRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * analysis.snapshot.v1의 가전 ON/OFF 상태를 직전 상태와 비교해 전환을 가려낸다.
 *
 * <p>스냅샷은 이상 징후가 없어도 계속 들어온다. 그래서 이 경로가 위험 평가의
 * "현재 값" 공급처다. 마지막 관측 시각(신선도), 당일 가전별 사용 사실,
 * 마지막 활동 시각이 모두 여기서 채워진다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplianceActivityService {

    /** 영업일 경계. 프로필의 일별 집계와 같은 시간대를 쓴다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SubjectRepository subjects;
    private final ApplianceStateRepository applianceStates;
    private final HouseholdObservationRepository observations;
    private final HouseholdDailyApplianceUsageRepository dailyUsage;
    private final RiskAssessmentService assessments;
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

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 관측 신선도를 먼저 남긴다. 전환이 없어도 "보고 있다"는 사실은 갱신돼야
        // 평가가 조용한 시간을 관측 두절로 오해하지 않는다.
        // 가구 행 락을 잡지 않는다. 상시로 들어오는 흐름이 이벤트 처리와 부딪히면 안 된다.
        recordObservation(message, now);

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
        List<String> turnedOn = new ArrayList<>();
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
            switch (previous.changeTo(appliance.isOn(), message.observedAt())) {
                case TURNED_ON -> turnedOn.add(appliance.applianceType());
                case TURNED_OFF -> turnedOff.add(appliance.applianceType());
                case NONE -> {
                    // 직전과 같은 상태다. 남길 것이 없다.
                }
            }
        }
        if (!created.isEmpty()) {
            applianceStates.saveAll(created);
        }

        LocalDate usageDate = message.observedAt().atZoneSameInstant(ZONE).toLocalDate();
        for (String appliance : turnedOn) {
            recordDailyUsage(message.householdId(), appliance, usageDate, message.observedAt());
        }

        if (turnedOff.isEmpty() && turnedOn.isEmpty()) {
            // 전환이 없으면 평가 입력도 그대로다. 매 스냅샷마다 평가하지 않는다.
            return;
        }

        // 분석 이벤트 소비와 같은 행을 갱신한다. 같은 락을 잡아 덮어쓰기를 막는다.
        List<Subject> matches = subjects.findHouseholdForUpdate(message.householdId());
        if (matches.size() != 1) {
            log.warn("가구에 대상자가 정확히 한 명이 아니어서 마지막 활동을 건너뛴다: householdId={}, 수={}",
                    message.householdId(), matches.size());
            return;
        }
        Subject subject = matches.get(0);

        if (!turnedOff.isEmpty()) {
            // 한 스냅샷에서 여러 대가 동시에 꺼지면 시각이 모두 같다.
            // 무엇을 골라도 등가이므로 코드 순으로 정해 결과를 재현 가능하게 둔다.
            String appliance = turnedOff.stream().min(Comparator.naturalOrder()).orElseThrow();
            if (subject.recordActivity(appliance, message.observedAt(), now)) {
                publisher.publishEvent(
                        new SubjectStateChanged(subject.getId(), StateChangeTrigger.ACTIVITY));
            }
            // 장시간 사용으로 세워 둔 이벤트 등급은 그 가전이 꺼지면 근거를 잃는다.
            if (assessments.releaseEventRiskFor(subject, turnedOff, now)) {
                // 슬롯이 비면 유효 등급이 내려간다. 담당자 화면이 알아야 한다.
                publisher.publishEvent(
                        new SubjectStateChanged(subject.getId(), StateChangeTrigger.ACTIVITY));
            }
        }

        // 상태가 바뀐 그 트랜잭션 안에서 평가한다.
        // 스냅샷이 롤백되면 그 상태로 낸 평가도 함께 사라져야 한다.
        assessments.evaluate(message.householdId(), now, StateChangeTrigger.ACTIVITY);
    }

    /**
     * 마지막 관측 시각을 갱신한다.
     *
     * <p>같은 가구의 스냅샷은 한 파티션으로 순서대로 들어오므로 읽고 쓰는 사이에
     * 끼어들 경쟁자가 없다. 드물게 겹치더라도 관측 시각이 되돌아가지 않는 것만 지키면 된다.
     */
    private void recordObservation(AnalysisSnapshotMessage message, OffsetDateTime now) {
        observations.findById(message.householdId())
                .ifPresentOrElse(
                        observation -> observation.observe(
                                message.observedAt(), message.publishedAt(), now),
                        () -> observations.save(new HouseholdObservation(
                                message.householdId(),
                                message.observedAt(),
                                message.publishedAt(),
                                now
                        ))
                );
    }

    /** 당일 가전별 사용 사실. 첫 사용 시각은 한 번만 채운다. */
    private void recordDailyUsage(
            String householdId,
            String applianceType,
            LocalDate usageDate,
            OffsetDateTime startedAt
    ) {
        dailyUsage
                .findById(new HouseholdDailyApplianceUsageId(householdId, applianceType, usageDate))
                .ifPresentOrElse(
                        usage -> usage.recordStart(startedAt),
                        () -> dailyUsage.save(new HouseholdDailyApplianceUsage(
                                householdId, applianceType, usageDate, startedAt))
                );
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
