package com.nilm.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.dto.kafka.AnalysisEventMessage;
import com.nilm.monitoring.dto.kafka.AnalysisSnapshotMessage;
import com.nilm.monitoring.service.AnalysisEventService;
import com.nilm.monitoring.service.ApplianceActivityService;
import com.nilm.monitoring.service.NotificationReady;
import com.nilm.monitoring.service.RiskAssessmentService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 모니터링 자체 평가가 등급을 세우고 알림을 내는 흐름.
 *
 * <p>평가 기준 시각을 KST 2026-09-21 12:10으로 고정하고 직접 넘긴다.
 * 히스테리시스와 재발송 간격은 시간의 함수라, 실제 시계로는 단정할 수 없다.
 */
@SpringBootTest
@RecordApplicationEvents
class RiskAssessmentFlowTest {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-09-21T12:10:00+09:00");

    /**
     * 기준 시각이 속한 시간대 구간. 배치 표기를 따라 구간의 끝 시각만 적는다.
     * KST 12:10은 12:00~12:30 구간이므로 "12:30"이다.
     */
    private static final String BUCKET = "12:30";

    /** 무활동 중앙값 1000초에서 z=5가 되는 경과시간. 점수 75 = 주의 등급이다. */
    private static final long WARNING_INACTIVITY_SECONDS = 5500;

    @Autowired RiskAssessmentService assessments;
    @Autowired AnalysisEventService analysisEvents;
    @Autowired ApplianceActivityService activities;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents applicationEvents;

    private Long subjectId;
    private Long managerId;

    @BeforeEach
    void setup() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from appliance_states");
        jdbc.update("delete from household_daily_appliance_usage");
        jdbc.update("delete from household_observations");
        jdbc.update("delete from household_profile_statistics");
        jdbc.update("delete from household_routine_baselines");
        jdbc.update("delete from household_profiles");
        jdbc.update("delete from subjects");
        jdbc.update("delete from notification_settings");
        jdbc.update("delete from managers");

        jdbc.update("""
                insert into managers(auth_sub, name, organization)
                values ('test-manager-1', '담당', '센터')
                """);
        managerId = jdbc.queryForObject("select id from managers", Long.class);
        jdbc.update("""
                insert into subjects(household_id, auth_sub, birth_date, name, phone, address,
                        manager_id)
                values ('house-risk', 'test-subject-3', DATE '1950-01-01', 'test', '010', 'test', ?)
                """, managerId);
        subjectId = jdbc.queryForObject("select id from subjects", Long.class);

        seedProfile();
        observe(BASE);
        applicationEvents.clear();
    }

    /** 무활동 비교 통계만 담은 프로필. 기준선이 없으므로 루틴 지표는 제외된다. */
    private void seedProfile() {
        jdbc.update("""
                insert into household_profiles(household_id, profile_version, as_of_date,
                        window_start_date, window_end_date, effective_from, received_at,
                        quality_status, status)
                values ('house-risk', 'profile-1', DATE '2026-09-20', DATE '2026-08-24',
                        DATE '2026-09-20', ?, ?, 'READY', 'ACTIVE')
                """, BASE.minusHours(6), BASE.minusHours(6));
        Long profileId = jdbc.queryForObject(
                "select id from household_profiles", Long.class);

        jdbc.update("""
                insert into household_profile_statistics(profile_id, metric_name, weekday_group,
                        time_bucket, sample_count, eligible_day_count, p50, p90, mad, unit,
                        quality_status)
                values (?, 'INACTIVITY_ELAPSED', 'ALL', ?, 20, 20, 1000, 4000, 0, 'seconds',
                        'READY')
                """, profileId, BUCKET);
    }

    /**
     * 평가가 볼 현재 상태를 기준 시각에 맞춘다.
     * 경과시간을 고정해야 매 평가에서 같은 점수가 나온다.
     */
    private void observe(OffsetDateTime now) {
        jdbc.update("delete from household_observations");
        jdbc.update("""
                insert into household_observations(household_id, last_observed_at, updated_at)
                values ('house-risk', ?, ?)
                """, now, now);
        jdbc.update("update subjects set last_activity_at = ?",
                now.minusSeconds(WARNING_INACTIVITY_SECONDS));
    }

    private void evaluateAt(OffsetDateTime now) {
        observe(now);
        assessments.evaluateSubject(subjectId, now, StateChangeTrigger.ASSESSMENT);
    }

    private String riskLevel() {
        return jdbc.queryForObject("select current_risk_level from subjects", String.class);
    }

    private int notificationCount() {
        return jdbc.queryForObject("select count(*) from notifications", Integer.class);
    }

    @Test
    void 임계를_넘겨도_유지시간_전에는_등급이_오르지_않는다() {
        evaluateAt(BASE);
        assertThat(riskLevel()).isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject(
                "select pending_risk_level from subjects", String.class)).isEqualTo("WARNING");
        assertThat(notificationCount()).isZero();

        // 유지시간(10분)에 못 미친다.
        evaluateAt(BASE.plusMinutes(5));
        assertThat(riskLevel()).isEqualTo("NORMAL");
        assertThat(notificationCount()).isZero();
    }

    @Test
    void 유지시간을_채우면_주의로_올라가고_알림을_한_건_만든다() {
        evaluateAt(BASE);
        evaluateAt(BASE.plusMinutes(11));

        assertThat(riskLevel()).isEqualTo("WARNING");
        assertThat(jdbc.queryForObject(
                "select assessed_risk_score from subjects", Integer.class)).isEqualTo(75);
        assertThat(jdbc.queryForObject(
                "select assessment_status from subjects", String.class)).isEqualTo("VALID");
        assertThat(notificationCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select subject_id from notifications", Long.class)).isEqualTo(subjectId);
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where assessment_id is not null",
                Integer.class)).isEqualTo(1);

        // 재발송 간격(1시간) 전에는 같은 등급으로 다시 알리지 않는다.
        evaluateAt(BASE.plusMinutes(12));
        assertThat(riskLevel()).isEqualTo("WARNING");
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void 평가가_성립하지_않으면_기존_점수를_덮어쓰지_않는다() {
        evaluateAt(BASE);
        evaluateAt(BASE.plusMinutes(11));
        assertThat(riskLevel()).isEqualTo("WARNING");

        // 비교할 프로필이 사라졌다. 학습 중으로 되돌아간다.
        jdbc.update("update household_profiles set status = 'SUPERSEDED'");
        evaluateAt(BASE.plusMinutes(20));

        assertThat(jdbc.queryForObject(
                "select assessment_status from subjects", String.class)).isEqualTo("LEARNING");
        // 평가 불가를 0점·정상으로 덮어쓰지 않는다.
        assertThat(jdbc.queryForObject(
                "select assessed_risk_level from subjects", String.class)).isEqualTo("WARNING");
        assertThat(jdbc.queryForObject(
                "select assessed_risk_score from subjects", Integer.class)).isEqualTo(75);
        assertThat(riskLevel()).isEqualTo("WARNING");
    }

    @Test
    void 담당자가_주의_알림을_꺼두면_행만_남기고_발송하지_않는다() {
        jdbc.update("""
                insert into notification_settings(manager_id, notification_type, channel, enabled)
                values (?, 'WARNING', 'PUSH', false)
                """, managerId);

        evaluateAt(BASE);
        evaluateAt(BASE.plusMinutes(11));

        assertThat(riskLevel()).isEqualTo("WARNING");
        // 담당자 화면의 미해결 사건은 발송 여부와 무관하게 사실 그대로 남아야 한다.
        assertThat(notificationCount()).isEqualTo(1);
        assertThat(applicationEvents.stream(NotificationReady.class).count()).isZero();
    }

    @Test
    void 평가_이력에_정책과_근거가_함께_남는다() {
        evaluateAt(BASE);

        assertThat(jdbc.queryForObject(
                "select count(*) from risk_assessments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select policy_version from risk_assessments", String.class))
                .isEqualTo("policy-v1-experimental");
        assertThat(jdbc.queryForObject(
                "select score_version from risk_assessments", String.class))
                .isEqualTo("monitoring-score-v1-MIA");
        assertThat(jdbc.queryForObject(
                "select profile_version from risk_assessments", String.class))
                .isEqualTo("profile-1");
        assertThat(jdbc.queryForObject(
                "select risk_score from risk_assessments", Integer.class)).isEqualTo(75);
        assertThat(jdbc.queryForObject(
                "select indicators from risk_assessments", String.class))
                .contains("\"code\":\"I\"")
                .contains("NO_BASELINE");
    }

    @Test
    void 이벤트_등급은_자체_평가보다_높을_때만_화면에_드러나고_가전이_꺼지면_해제된다() {
        evaluateAt(BASE);
        evaluateAt(BASE.plusMinutes(11));
        assertThat(riskLevel()).isEqualTo("WARNING");

        OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);
        // 켜진 상태를 먼저 심어 둔다. 첫 스냅샷은 전환으로 보지 않는다.
        activities.handle(snapshot(observedAt, true));

        analysisEvents.handle(new AnalysisEventMessage(
                UUID.randomUUID(),
                "house-risk",
                null,
                observedAt,
                Map.of("appliance_type", "KETTLE", "allowed_duration_minutes", 60),
                "PROLONGED_APPLIANCE_USE",
                "KETTLE"
        ));

        // 이벤트 등급이 자체 평가 등급보다 높아 유효 등급이 된다.
        assertThat(riskLevel()).isEqualTo("DANGER");
        assertThat(jdbc.queryForObject(
                "select event_risk_appliance from subjects", String.class)).isEqualTo("KETTLE");

        // 그 가전이 꺼지면 이벤트 등급은 근거를 잃는다.
        activities.handle(snapshot(observedAt.plusMinutes(5), false));

        assertThat(jdbc.queryForObject(
                "select event_risk_level from subjects", String.class)).isNull();
        // 유효 등급은 자체 평가 등급으로 되돌아간다.
        assertThat(riskLevel()).isEqualTo("WARNING");
        assertThat(jdbc.queryForObject(
                "select current_risk_score from subjects", Integer.class)).isEqualTo(75);
    }

    private AnalysisSnapshotMessage snapshot(OffsetDateTime observedAt, boolean kettleOn) {
        return new AnalysisSnapshotMessage(
                1,
                UUID.randomUUID().toString(),
                "house-risk",
                observedAt,
                observedAt,
                new AnalysisSnapshotMessage.Measurement(null, null, null, null),
                List.of(new AnalysisSnapshotMessage.Appliance("KETTLE", kettleOn))
        );
    }
}
