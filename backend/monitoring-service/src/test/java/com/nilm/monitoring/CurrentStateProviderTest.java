package com.nilm.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.nilm.monitoring.config.RiskProperties;
import com.nilm.monitoring.dto.kafka.AnalysisSnapshotMessage;
import com.nilm.monitoring.risk.AssessmentStatus;
import com.nilm.monitoring.risk.CurrentState;
import com.nilm.monitoring.risk.IndicatorResult;
import com.nilm.monitoring.risk.RiskAssessment;
import com.nilm.monitoring.risk.RiskAssessor;
import com.nilm.monitoring.service.ApplianceActivityService;
import com.nilm.monitoring.service.CurrentStateProvider;
import com.nilm.monitoring.service.ResolvedProfile;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스냅샷 소비가 남긴 값에서 평가 입력을 만드는 경로.
 *
 * <p>통합 단계에서 {@code RiskAssessmentService.currentState()}가 이 제공자로 바뀐다.
 * 그래서 여기서는 스냅샷을 실제로 흘려 넣고, 그 결과로 만들어진 입력으로 평가까지 돌려
 * 관측 공백이 위험으로 둔갑하지 않는지 확인한다.
 */
@SpringBootTest
class CurrentStateProviderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 2026-05-11(월) 12:10 KST. 하루 중 초는 43800이다. */
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-11T12:10:00+09:00");

    private static final OffsetDateTime DAY_START =
            OffsetDateTime.parse("2026-05-11T00:00:00+09:00");

    @Autowired ApplianceActivityService activities;
    @Autowired CurrentStateProvider currentStates;
    @Autowired RiskProperties riskProperties;
    @Autowired JdbcTemplate jdbc;

    private final RiskAssessor assessor = new RiskAssessor();

    @BeforeEach
    void setup() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from household_daily_appliance_usage");
        jdbc.update("delete from appliance_usage_episodes");
        jdbc.update("delete from appliance_states");
        jdbc.update("delete from household_observations");
        jdbc.update("delete from subjects");
        jdbc.update("""
                insert into subjects(household_id, auth_sub, birth_date, name, phone, address)
                values ('H100', 'test-subject-3', DATE '1950-01-01', 'test', '010', 'test')
                """);
    }

    private AnalysisSnapshotMessage snapshot(OffsetDateTime observedAt, String onAppliance) {
        return new AnalysisSnapshotMessage(
                1,
                UUID.randomUUID().toString(),
                "H100",
                observedAt,
                observedAt,
                new AnalysisSnapshotMessage.Measurement(null, null, null, null),
                List.of(
                        new AnalysisSnapshotMessage.Appliance(
                                "KETTLE", "KETTLE".equals(onAppliance)),
                        new AnalysisSnapshotMessage.Appliance(
                                "MICROWAVE", "MICROWAVE".equals(onAppliance))));
    }

    /**
     * 주어진 구간을 공백 없이 관측한다. 간격은 공백 기준(120초)과 같게 둔다.
     * 기준 이내이므로 그 사이는 모두 본 구간으로 적힌다.
     */
    private void observeContinuously(OffsetDateTime from, OffsetDateTime until) {
        for (OffsetDateTime at = from; !at.isAfter(until); at = at.plusSeconds(120)) {
            activities.handle(snapshot(at, null));
        }
    }

    /** 전기포트 기준선 하나뿐인 프로필. 첫 사용 P90은 07:00이다. */
    private ResolvedProfile profile() {
        return new ResolvedProfile(
                "profile-1",
                LocalDate.of(2026, 5, 10),
                NOW.minusHours(6),
                false,
                List.of(new ResolvedProfile.RoutineBaseline(
                        "KETTLE", "OVERALL", null, 20, 18,
                        BigDecimal.valueOf(1.0), BigDecimal.ONE,
                        21600, 25200, null, null, "READY", true)),
                Map.of());
    }

    private IndicatorResult indicator(RiskAssessment assessment, String code) {
        return assessment.indicators().stream()
                .filter(result -> code.equals(result.code()))
                .findFirst()
                .orElseThrow();
    }

    private RiskAssessment assess(CurrentState state) {
        return assessor.assess(Optional.of(profile()), state, riskProperties.toPolicy());
    }

    @Test
    void 하루를_계속_본_가구는_루틴_미사용을_계산한다() {
        observeContinuously(DAY_START, NOW);

        CurrentState state = currentStates.of("H100", false, NOW);
        RiskAssessment assessment = assess(state);

        assertThat(state.observation().coverageTracked()).isTrue();
        assertThat(state.activity().precise()).isTrue();
        // 07:00까지는 쓰던 가전을 12:10까지 쓰지 않았다. 그 사실을 끝까지 봤다.
        assertThat(indicator(assessment, "M").included()).isTrue();
        assertThat(indicator(assessment, "M").score()).isEqualTo(1.0);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.VALID);
    }

    @Test
    void 공백_뒤_스냅샷_하나로는_하루_사용_부재를_확정하지_않는다() {
        // 하루의 시작에 한 번 보고, 공백을 지나 방금 한 번 더 봤다.
        activities.handle(snapshot(DAY_START, null));
        activities.handle(snapshot(NOW, null));

        CurrentState state = currentStates.of("H100", false, NOW);
        RiskAssessment assessment = assess(state);

        // 신선도는 회복됐다. 그 한 건이 공백을 증명하지는 않는다.
        assertThat(state.observation().lastObservedAt()).isEqualTo(NOW);
        assertThat(indicator(assessment, "M").excludedReason()).isEqualTo("OBSERVATION_COVERAGE");
        assertThat(assessment.level()).isNull();
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    @Test
    void 관측이_끊긴_채로는_위험_등급을_내지_않는다() {
        // 하루를 계속 보다가 4시간 전에 끊겼다.
        observeContinuously(DAY_START, NOW.minusHours(4));

        RiskAssessment assessment = assess(currentStates.of("H100", false, NOW));

        assertThat(indicator(assessment, "M").excludedReason()).isEqualTo("OBSERVATION_STALE");
        assertThat(assessment.status()).isNotEqualTo(AssessmentStatus.VALID);
        assertThat(assessment.level()).isNull();
        assertThat(assessment.score()).isNull();
    }

    @Test
    void 짧은_ON_신호는_사용으로_세지_않는다() {
        observeContinuously(DAY_START, DAY_START.plusHours(8));
        // 5초 만에 꺼진 신호. Gold는 이것을 사용으로 세지 않는다.
        activities.handle(snapshot(DAY_START.plusHours(8).plusSeconds(10), "KETTLE"));
        activities.handle(snapshot(DAY_START.plusHours(8).plusSeconds(15), null));
        observeContinuously(DAY_START.plusHours(8).plusSeconds(135), NOW);

        CurrentState state = currentStates.of("H100", false, NOW);

        assertThat(state.activity().used("KETTLE", DAY_START, NOW)).isFalse();
        assertThat(indicator(assess(state), "M").score()).isEqualTo(1.0);
    }

    @Test
    void 유효한_사용이_있으면_루틴_미사용이_아니다() {
        observeContinuously(DAY_START, DAY_START.plusHours(6));
        activities.handle(snapshot(DAY_START.plusHours(6).plusSeconds(10), "KETTLE"));
        activities.handle(snapshot(DAY_START.plusHours(6).plusSeconds(70), null));
        observeContinuously(DAY_START.plusHours(6).plusSeconds(190), NOW);

        CurrentState state = currentStates.of("H100", false, NOW);

        assertThat(state.activity().used("KETTLE", DAY_START, NOW)).isTrue();
        assertThat(state.activity().startCount(LocalDate.of(2026, 5, 11), NOW))
                .hasValue(1);
        assertThat(indicator(assess(state), "M").score()).isZero();
    }

    @Test
    void 공백을_지난_뒤에는_끊김_없는_관측의_시작이_공백_뒤로_옮겨진다() {
        observeContinuously(DAY_START, DAY_START.plusHours(2));
        OffsetDateTime afterGap = DAY_START.plusHours(6);
        activities.handle(snapshot(afterGap, null));
        observeContinuously(afterGap.plusSeconds(120), NOW);

        CurrentState state = currentStates.of("H100", false, NOW);

        assertThat(state.observation().continuousSince()).isEqualTo(afterGap);
        // 공백 4시간만큼 커버리지가 비어 있다.
        assertThat(state.observation().coverageRatio(LocalDate.of(2026, 5, 11), DAY_START, NOW))
                .isLessThan(riskProperties.getMinObservationCoverage());
    }

    @Test
    void 관측_공백_기준보다_촘촘한_스냅샷만_커버리지로_센다() {
        Duration threshold = riskProperties.getObservationGapThreshold();
        assertThat(threshold).isEqualTo(Duration.ofSeconds(120));

        activities.handle(snapshot(DAY_START, null));
        // 기준 이내라 그 사이를 본 것으로 적는다.
        activities.handle(snapshot(DAY_START.plusSeconds(120), null));
        // 기준을 넘겼다. 그 사이는 보지 못한 구간이다.
        activities.handle(snapshot(DAY_START.plusSeconds(400), null));

        assertThat(jdbc.queryForObject(
                "select covered_seconds from household_observations", Long.class))
                .isEqualTo(120L);
    }
}
