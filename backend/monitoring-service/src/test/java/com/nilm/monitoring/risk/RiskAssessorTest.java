package com.nilm.monitoring.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.nilm.monitoring.config.enums.RiskLevel;
import com.nilm.monitoring.service.ResolvedProfile;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 점수식 자체를 Spring 없이 검증한다.
 *
 * <p>기준 시각은 KST 2026-09-21 12:10으로 고정한다. 하루 중 초(43800)와
 * 시간대 구간이 결과를 좌우하므로 "지금"이 흔들리면 단정도 흔들린다.
 */
class RiskAssessorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-21T12:10:00+09:00");

    /** 12:10 KST를 하루의 초로 옮긴 값. */
    private static final int NOW_SECOND = 12 * 3600 + 10 * 60;

    /** 배치 표기를 따라 구간의 끝 시각만 적는다. 12:10은 12:00~12:30 구간이다. */
    private static final String BUCKET = "12:30";

    private final RiskAssessor assessor = new RiskAssessor();

    private RiskPolicy policy() {
        return new RiskPolicy(
                "policy-test",
                70,
                90,
                Duration.ofMinutes(10),
                60,
                Duration.ofMinutes(30),
                2,
                6,
                900,
                Duration.ofMinutes(30),
                Duration.ofDays(3),
                Duration.ofMinutes(15),
                Duration.ofHours(1),
                7,
                14
        );
    }

    private ResolvedProfile profile(
            List<ResolvedProfile.RoutineBaseline> baselines,
            List<ResolvedProfile.Statistic> statistics
    ) {
        return profile(baselines, statistics, false);
    }

    private ResolvedProfile profile(
            List<ResolvedProfile.RoutineBaseline> baselines,
            List<ResolvedProfile.Statistic> statistics,
            boolean stale
    ) {
        Map<String, ResolvedProfile.Statistic> byKey = new java.util.LinkedHashMap<>();
        for (ResolvedProfile.Statistic statistic : statistics) {
            byKey.put(
                    ResolvedProfile.statisticKey(
                            statistic.metricName(),
                            statistic.applianceType(),
                            statistic.weekdayGroup(),
                            statistic.timeBucket()),
                    statistic);
        }
        return new ResolvedProfile(
                "profile-1",
                LocalDate.of(2026, 9, 20),
                NOW.minusHours(6),
                stale,
                baselines,
                Map.copyOf(byKey)
        );
    }

    /** 가전 기준선 한 줄. 평가에 쓸 수 있는 상태(enabled + READY)로 만든다. */
    private ResolvedProfile.RoutineBaseline baseline(
            String applianceType,
            Integer p50Second,
            Integer expectedUntilSecond,
            double dailyUseProbability
    ) {
        return new ResolvedProfile.RoutineBaseline(
                applianceType,
                "OVERALL",
                null,
                20,
                18,
                BigDecimal.valueOf(dailyUseProbability),
                BigDecimal.ONE,
                p50Second,
                expectedUntilSecond,
                null,
                null,
                "READY",
                true
        );
    }

    /** 시간대별 통계 한 줄. weekday_group은 배치가 쓰는 ALL로 둔다. */
    private ResolvedProfile.Statistic statistic(
            String metricName,
            double p50,
            double mad,
            long sampleCount,
            long eligibleDayCount
    ) {
        return new ResolvedProfile.Statistic(
                metricName,
                null,
                "ALL",
                BUCKET,
                sampleCount,
                eligibleDayCount,
                p50,
                p50,
                mad,
                "seconds",
                "READY"
        );
    }

    private CurrentState state(
            boolean away,
            OffsetDateTime lastObservedAt,
            OffsetDateTime lastActivityEndedAt,
            Map<String, CurrentState.DailyUsage> todayUsage
    ) {
        return new CurrentState(NOW, away, lastObservedAt, lastActivityEndedAt, false, todayUsage);
    }

    private IndicatorResult indicator(RiskAssessment assessment, String code) {
        return assessment.indicators().stream()
                .filter(result -> code.equals(result.code()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 첫_사용_P90_이전에는_루틴_미사용_점수가_0이다() {
        // 평소 쓰던 시각(14:00)이 아직 지나지 않았다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(baseline("KETTLE", 46800, 50400, 0.9)), List.of())),
                state(false, NOW, null, Map.of()),
                policy());

        assertThat(indicator(assessment, "M").score()).isZero();
        assertThat(assessment.score()).isZero();
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.VALID);
        assertThat(assessment.level()).isEqualTo(RiskLevel.NORMAL);
    }

    @Test
    void 오늘_이미_사용한_가전은_미사용으로_보지_않는다() {
        // 기준선만 보면 한참 늦었지만 오늘 첫 사용 기록이 있다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(baseline("KETTLE", 21600, 25200, 0.9)), List.of())),
                state(false, NOW, null,
                        Map.of("KETTLE", new CurrentState.DailyUsage(NOW.minusHours(3), 2))),
                policy());

        assertThat(indicator(assessment, "M").score()).isZero();
        assertThat(assessment.score()).isZero();
    }

    @Test
    void 지연은_P90부터_재고_사용일비율을_곱한다() {
        // P90 11:00, P50 10:00 이므로 유예 폭은 3600초. 12:10이면 4200초 늦었다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(baseline("KETTLE", 36000, 39600, 0.8)), List.of())),
                state(false, NOW, null, Map.of()),
                policy());

        // clip(4200/3600) = 1, 0.8 x 1 = 0.8
        assertThat(indicator(assessment, "M").score()).isEqualTo(0.8);
        assertThat(assessment.score()).isEqualTo(80);
        assertThat(assessment.level()).isEqualTo(RiskLevel.WARNING);
    }

    @Test
    void P90와_P50_간격이_최소_유예보다_좁으면_최소_유예를_쓴다() {
        // P90 11:55, P50 11:45. 간격 600초는 최소 유예 1800초보다 좁다.
        var narrow = assessor.assess(
                Optional.of(profile(List.of(baseline("KETTLE", 42300, 42900, 1.0)), List.of())),
                state(false, NOW, null, Map.of()),
                policy());

        // 간격을 그대로 썼다면 900/600 = 1.5 -> clip 1점이 됐을 것이다.
        assertThat(indicator(narrow, "M").compareSpread()).isEqualTo(1800.0);
        assertThat(indicator(narrow, "M").score()).isEqualTo(900.0 / 1800.0);
        assertThat(narrow.score()).isEqualTo(50);
    }

    @Test
    void MAD가_0이어도_최소_변동폭으로_나눈다() {
        // mad=0이면 분모가 0이 된다. 최소 변동폭 900초가 바닥을 깐다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                state(false, NOW, NOW.minusSeconds(5500), Map.of()),
                policy());

        IndicatorResult inactivity = indicator(assessment, "I");
        assertThat(inactivity.compareSpread()).isEqualTo(900.0);
        // z = (5500-1000)/900 = 5, clip((5-2)/4) = 0.75
        assertThat(inactivity.score()).isEqualTo(0.75);
        assertThat(assessment.score()).isEqualTo(75);
        assertThat(assessment.level()).isEqualTo(RiskLevel.WARNING);
    }

    @Test
    void z가_하한_이하면_0이고_상한_이상이면_1이다() {
        var atLow = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                // z = (2800-1000)/900 = 2 = zLow
                state(false, NOW, NOW.minusSeconds(2800), Map.of()),
                policy());
        assertThat(indicator(atLow, "I").score()).isZero();

        var atHigh = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                // z = (6400-1000)/900 = 6 = zHigh
                state(false, NOW, NOW.minusSeconds(6400), Map.of()),
                policy());
        assertThat(indicator(atHigh, "I").score()).isEqualTo(1.0);
        assertThat(atHigh.score()).isEqualTo(100);
        assertThat(atHigh.level()).isEqualTo(RiskLevel.DANGER);
    }

    @Test
    void 외출_중에는_모든_지표를_제외하고_등급을_내지_않는다() {
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 21600, 25200, 0.9)),
                        List.of(statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                state(true, NOW, NOW.minusSeconds(6400), Map.of()),
                policy());

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.level()).isNull();
        assertThat(assessment.score()).isNull();
        assertThat(assessment.indicators())
                .allSatisfy(result -> assertThat(result.excludedReason()).isEqualTo("AWAY"));
    }

    @Test
    void 관측이_끊기면_무활동과_활동량만_제외하고_루틴으로_평가한다() {
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 36000, 39600, 0.8)),
                        List.of(statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                // 관측이 1시간 끊겼다. 허용치는 15분이다.
                state(false, NOW.minusHours(1), NOW.minusSeconds(6400), Map.of()),
                policy());

        assertThat(indicator(assessment, "I").excludedReason()).isEqualTo("OBSERVATION_STALE");
        assertThat(indicator(assessment, "A").excludedReason()).isEqualTo("OBSERVATION_STALE");
        assertThat(indicator(assessment, "M").included()).isTrue();
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.VALID);
        // 무활동이 더 높은 점수를 냈겠지만 제외됐으므로 루틴 점수만 남는다.
        assertThat(assessment.score()).isEqualTo(80);
    }

    @Test
    void 프로필이_없으면_학습_중으로_보고_점수를_내지_않는다() {
        var assessment = assessor.assess(
                Optional.empty(),
                state(false, NOW, NOW.minusSeconds(6400), Map.of()),
                policy());

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.LEARNING);
        assertThat(assessment.score()).isNull();
        assertThat(assessment.level()).isNull();
    }

    @Test
    void 유효기간을_넘긴_프로필로는_평가하지_않는다() {
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 36000, 39600, 0.8)),
                        List.of(statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)),
                        true)),
                state(false, NOW, NOW.minusSeconds(6400), Map.of()),
                policy());

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.score()).isNull();
        assertThat(assessment.level()).isNull();
    }

    @Test
    void 표본이_모자란_통계는_믿지_않는다() {
        var assessment = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        // 최소 표본 7에 못 미친다.
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 3, 3)))),
                state(false, NOW, NOW.minusSeconds(6400), Map.of()),
                policy());

        assertThat(indicator(assessment, "I").excludedReason()).isEqualTo("INSUFFICIENT_SAMPLE");
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.score()).isNull();
    }

    @Test
    void 루틴과_무활동이_모두_제외되면_점수를_내지_않는다() {
        // 기준선도 통계도 없다. 누락 지표를 0으로 채우면 "정상"으로 보이므로 점수를 비운다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(), List.of())),
                state(false, NOW, NOW.minusSeconds(6400), Map.of()),
                policy());

        assertThat(indicator(assessment, "M").excludedReason()).isEqualTo("NO_BASELINE");
        assertThat(indicator(assessment, "I").excludedReason()).isEqualTo("NO_STATISTIC");
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.score()).isNull();
        assertThat(assessment.level()).isNull();
    }

    @Test
    void 신뢰도가_낮아도_점수를_깎지_않는다() {
        var manyDays = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 28)))),
                state(false, NOW, NOW.minusSeconds(5500), Map.of()),
                policy());
        var fewDays = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 7)))),
                state(false, NOW, NOW.minusSeconds(5500), Map.of()),
                policy());

        assertThat(manyDays.confidence()).isEqualTo(1.0);
        assertThat(fewDays.confidence()).isEqualTo(0.5);
        // 신뢰도는 점수에 곱하지 않는다. 데이터 부족이 낮은 위험으로 둔갑하면 안 된다.
        assertThat(fewDays.score()).isEqualTo(manyDays.score()).isEqualTo(75);
        assertThat(fewDays.level()).isEqualTo(manyDays.level());
    }

    @Test
    void 하루_중_초와_시간대_구간이_기준_시각과_맞는다() {
        // 다른 단정들이 기대하는 "지금"이 실제로 12:10 KST인지 확인한다.
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 36000, 39600, 0.8)),
                        List.of(statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                state(false, NOW, NOW.minusSeconds(5500), Map.of()),
                policy());

        assertThat(indicator(assessment, "M").observed()).isEqualTo((double) NOW_SECOND);
        assertThat(indicator(assessment, "I").bucket()).isEqualTo(BUCKET);
    }
}
