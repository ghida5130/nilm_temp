package com.nilm.monitoring.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.nilm.monitoring.config.enums.RiskLevel;
import com.nilm.monitoring.service.ResolvedProfile;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 점수식 자체를 Spring 없이 검증한다.
 *
 * <p>기준 시각은 KST 2026-09-21(월) 12:10으로 고정한다. 하루 중 초(43800)와
 * 시간대 구간이 결과를 좌우하므로 "지금"이 흔들리면 단정도 흔들린다.
 *
 * <p>프로필 통계와 맞대는 시각은 12:10이 아니라 그 이하의 가장 최근 구간 경계인 12:00이다.
 * 프로필의 "12:30"은 12:30 정각에 잰 값이라, 12:10에 그 줄을 꺼내 쓰면 아직 오지 않은
 * 20분치 활동을 이미 했어야 하는 것으로 비교하게 된다.
 */
class RiskAssessorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-21T12:10:00+09:00");

    /** 12:10 KST를 하루의 초로 옮긴 값. */
    private static final int NOW_SECOND = 12 * 3600 + 10 * 60;

    /** 비교 기준 시각. 12:10 이하의 가장 최근 구간 경계다. */
    private static final OffsetDateTime EVAL = OffsetDateTime.parse("2026-09-21T12:00:00+09:00");

    /** 배치 표기를 따라 구간의 끝 시각만 적는다. 기준 시각 12:00의 구간 이름이다. */
    private static final String BUCKET = "12:00";

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static final OffsetDateTime DAY_START =
            OffsetDateTime.parse("2026-09-21T00:00:00+09:00");

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
                14,
                0.95
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
        return statistic(metricName, BUCKET, p50, mad, sampleCount, eligibleDayCount);
    }

    private ResolvedProfile.Statistic statistic(
            String metricName,
            String timeBucket,
            double p50,
            double mad,
            long sampleCount,
            long eligibleDayCount
    ) {
        return new ResolvedProfile.Statistic(
                metricName,
                null,
                "ALL",
                timeBucket,
                sampleCount,
                eligibleDayCount,
                p50,
                p50,
                mad,
                "seconds",
                "READY"
        );
    }

    /** 오늘을 빠짐없이 봤고 방금 전에도 보고 있는 관측 품질. */
    private ObservationQuality observed(OffsetDateTime lastObservedAt) {
        long covered = Duration.between(DAY_START, lastObservedAt).getSeconds();
        return new ObservationQuality(
                lastObservedAt,
                true,
                DAY_START.minusDays(2),
                Map.of(TODAY, Math.max(0, covered), TODAY.minusDays(1), 86_400L));
    }

    /** 끝난 유효 사용 한 건. */
    private ActivityLedger.Use use(String applianceType, OffsetDateTime from, OffsetDateTime to) {
        return new ActivityLedger.Use(
                applianceType, from, to, false, from.atZoneSameInstant(KST).toLocalDate());
    }

    /**
     * 마지막 활동이 {@code lastActivityEndedAt}에 끝난 상태.
     * 무활동 경과는 기준 시각(12:00)에서 잰다.
     */
    private CurrentState state(
            boolean away,
            OffsetDateTime lastObservedAt,
            OffsetDateTime lastActivityEndedAt,
            ActivityLedger.Use... extra
    ) {
        List<ActivityLedger.Use> uses = new ArrayList<>(List.of(extra));
        if (lastActivityEndedAt != null) {
            uses.add(use("MICROWAVE", lastActivityEndedAt.minusMinutes(5), lastActivityEndedAt));
        }
        return new CurrentState(
                NOW, away, observed(lastObservedAt), ActivityLedger.of(uses));
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
                state(false, NOW, null),
                policy());

        assertThat(indicator(assessment, "M").score()).isZero();
        assertThat(assessment.score()).isZero();
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.VALID);
        assertThat(assessment.level()).isEqualTo(RiskLevel.NORMAL);
    }

    @Test
    void 오늘_이미_사용한_가전은_미사용으로_보지_않는다() {
        // 기준선만 보면 한참 늦었지만 오늘 유효 사용 기록이 있다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(baseline("KETTLE", 21600, 25200, 0.9)), List.of())),
                state(false, NOW, null,
                        use("KETTLE", NOW.minusHours(3), NOW.minusHours(3).plusMinutes(4))),
                policy());

        assertThat(indicator(assessment, "M").score()).isZero();
        assertThat(assessment.score()).isZero();
    }

    @Test
    void 지연은_P90부터_재고_사용일비율을_곱한다() {
        // P90 11:00, P50 10:00 이므로 유예 폭은 3600초. 12:10이면 4200초 늦었다.
        var assessment = assessor.assess(
                Optional.of(profile(List.of(baseline("KETTLE", 36000, 39600, 0.8)), List.of())),
                state(false, NOW, null),
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
                state(false, NOW, null),
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
                state(false, NOW, EVAL.minusSeconds(5500)),
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
                state(false, NOW, EVAL.minusSeconds(2800)),
                policy());
        assertThat(indicator(atLow, "I").score()).isZero();

        var atHigh = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                // z = (6400-1000)/900 = 6 = zHigh
                state(false, NOW, EVAL.minusSeconds(6400)),
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
                state(true, NOW, EVAL.minusSeconds(6400)),
                policy());

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.level()).isNull();
        assertThat(assessment.score()).isNull();
        assertThat(assessment.indicators())
                .allSatisfy(result -> assertThat(result.excludedReason()).isEqualTo("AWAY"));
    }

    @Test
    void 관측이_끊기면_루틴_미사용도_계산하지_않는다() {
        // 마지막 관측이 4시간 전이고 오늘 사용 기록이 없다. 예전에는 이 입력에서
        // M만 살아남아 100점 DANGER VALID가 나왔다. 사용하지 않은 것이 아니라
        // 사용 여부를 볼 수 없었던 것이므로 미사용으로 확정하지 않는다.
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 21600, 25200, 1.0)),
                        List.of(statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                state(false, NOW.minusHours(4), EVAL.minusSeconds(6400)),
                policy());

        assertThat(indicator(assessment, "M").excludedReason()).isEqualTo("OBSERVATION_STALE");
        assertThat(indicator(assessment, "I").excludedReason()).isEqualTo("OBSERVATION_STALE");
        assertThat(indicator(assessment, "A").excludedReason()).isEqualTo("OBSERVATION_STALE");
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.score()).isNull();
        assertThat(assessment.level()).isNull();
    }

    @Test
    void 공백_뒤_스냅샷_하나로는_하루_사용_부재를_확정하지_않는다() {
        // 방금 스냅샷이 도착해 신선도는 회복됐지만 오늘의 절반만 봤다.
        // 그 한 건은 공백 동안 무슨 일이 있었는지 말해 주지 않는다.
        ObservationQuality halfSeen = new ObservationQuality(
                NOW,
                true,
                NOW.minusMinutes(1),
                Map.of(TODAY, Duration.between(DAY_START, NOW).getSeconds() / 2));
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 21600, 25200, 1.0)),
                        List.of(statistic("CUMULATIVE_ACTIVITY_START_COUNT", 5, 0, 20, 20)))),
                new CurrentState(NOW, false, halfSeen, ActivityLedger.of(List.of())),
                policy());

        assertThat(indicator(assessment, "M").excludedReason())
                .isEqualTo("OBSERVATION_COVERAGE");
        assertThat(indicator(assessment, "A").excludedReason())
                .isEqualTo("OBSERVATION_COVERAGE");
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.level()).isNull();
    }

    @Test
    void 커버리지를_집계하지_않는_입력은_사용_부재를_확정하지_않는다() {
        // 옛 입력 계약은 마지막 관측 시각만 싣는다. 모르는 구간을 정상 관측으로 바꾸지 않는다.
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 21600, 25200, 1.0)),
                        List.of(statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                new CurrentState(
                        NOW, false, NOW, EVAL.minusSeconds(5500), false, Map.of()),
                policy());

        assertThat(indicator(assessment, "M").excludedReason()).isEqualTo("USAGE_UNVERIFIED");
        assertThat(indicator(assessment, "A").excludedReason()).isEqualTo("USAGE_UNVERIFIED");
        // 무활동은 옛 입력으로도 잴 수 있다. 통합 전까지 그 경로는 그대로 돈다.
        assertThat(indicator(assessment, "I").included()).isTrue();
        assertThat(assessment.score()).isEqualTo(75);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.VALID);
    }

    @Test
    void 무활동으로_세려는_구간에_관측_공백이_있으면_제외한다() {
        // 경과 5500초 중간에 관측이 끊겼다 이어졌다. 그 사이의 활동은 보지 못했다.
        ObservationQuality afterGap = new ObservationQuality(
                NOW,
                true,
                EVAL.minusSeconds(1200),
                Map.of(TODAY, Duration.between(DAY_START, NOW).getSeconds()));
        var assessment = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20)))),
                new CurrentState(NOW, false, afterGap, ActivityLedger.of(List.of(
                        use("MICROWAVE", EVAL.minusSeconds(5800), EVAL.minusSeconds(5500))))),
                policy());

        assertThat(indicator(assessment, "I").excludedReason()).isEqualTo("OBSERVATION_GAP");
    }

    @Test
    void 프로필이_없으면_학습_중으로_보고_점수를_내지_않는다() {
        var assessment = assessor.assess(
                Optional.empty(),
                state(false, NOW, EVAL.minusSeconds(6400)),
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
                state(false, NOW, EVAL.minusSeconds(6400)),
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
                state(false, NOW, EVAL.minusSeconds(6400)),
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
                state(false, NOW, EVAL.minusSeconds(6400)),
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
                state(false, NOW, EVAL.minusSeconds(5500)),
                policy());
        var fewDays = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 7)))),
                state(false, NOW, EVAL.minusSeconds(5500)),
                policy());

        assertThat(manyDays.confidence()).isEqualTo(1.0);
        assertThat(fewDays.confidence()).isEqualTo(0.5);
        // 신뢰도는 점수에 곱하지 않는다. 데이터 부족이 낮은 위험으로 둔갑하면 안 된다.
        assertThat(fewDays.score()).isEqualTo(manyDays.score()).isEqualTo(75);
        assertThat(fewDays.level()).isEqualTo(manyDays.level());
    }

    @Test
    void 하루_중_초와_시간대_구간이_기준_시각과_맞는다() {
        // 12:10에 비교하는 구간은 12:30이 아니라 12:00이다.
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("KETTLE", 36000, 39600, 0.8)),
                        List.of(
                                statistic("INACTIVITY_ELAPSED", 1000, 0, 20, 20),
                                // 아직 오지 않은 구간의 통계는 쓰이지 않아야 한다.
                                statistic("INACTIVITY_ELAPSED", "12:30", 5, 0, 20, 20)))),
                state(false, NOW, EVAL.minusSeconds(5500)),
                policy());

        assertThat(indicator(assessment, "M").observed()).isEqualTo((double) NOW_SECOND);
        assertThat(indicator(assessment, "I").bucket()).isEqualTo(BUCKET);
        assertThat(indicator(assessment, "I").compareCenter()).isEqualTo(1000.0);
        // 경과시간도 12:00에서 잰다. 12:10 기준이면 6100초가 됐을 것이다.
        assertThat(indicator(assessment, "I").observed()).isEqualTo(5500.0);
    }

    @Test
    void 활동_감소는_기준_시각_전에_시작한_사용만_센다() {
        var assessment = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("CUMULATIVE_ACTIVITY_START_COUNT", 5, 0, 20, 20)))),
                new CurrentState(NOW, false, observed(NOW), ActivityLedger.of(List.of(
                        use("KETTLE", DAY_START.plusHours(7), DAY_START.plusHours(7).plusMinutes(3)),
                        // 12:05 시작. 기준 시각 12:00에는 아직 일어나지 않았다.
                        use("MICROWAVE", NOW.minusMinutes(5), NOW.minusMinutes(2))))),
                policy());

        IndicatorResult activity = indicator(assessment, "A");
        assertThat(activity.observed()).isEqualTo(1.0);
        assertThat(activity.bucket()).isEqualTo(BUCKET);
        // z = (5-1)/1 = 4, clip((4-2)/4) = 0.5
        assertThat(activity.score()).isEqualTo(0.5);
    }

    @Test
    void 자정을_넘겨_이어진_사용은_새_날의_시작으로_세지_않는다() {
        // 어제 23:50에 시작해 오늘 00:20에 끝난 사용 하나뿐이다.
        ActivityLedger.Use overnight = use(
                "IRON", DAY_START.minusMinutes(10), DAY_START.plusMinutes(20));
        var assessment = assessor.assess(
                Optional.of(profile(
                        List.of(baseline("IRON", 21600, 25200, 1.0)),
                        List.of(statistic("CUMULATIVE_ACTIVITY_START_COUNT", 5, 0, 20, 20)))),
                new CurrentState(NOW, false, observed(NOW), ActivityLedger.of(List.of(overnight))),
                policy());

        // 시작은 어제에만 든다. 오늘의 누적 시작은 0이다.
        assertThat(indicator(assessment, "A").observed()).isZero();
        // 그래도 오늘 그 가전을 쓴 것은 사실이라 루틴 미사용은 아니다.
        assertThat(indicator(assessment, "M").score()).isZero();
    }

    @Test
    void 자정_직후에는_전날의_24시_구간과_비교한다() {
        OffsetDateTime justAfterMidnight = OffsetDateTime.parse("2026-09-21T00:10:00+09:00");
        OffsetDateTime yesterdayStart = DAY_START.minusDays(1);
        ObservationQuality quality = new ObservationQuality(
                justAfterMidnight,
                true,
                yesterdayStart.minusDays(1),
                Map.of(TODAY.minusDays(1), 86_400L, TODAY, 600L));
        var assessment = assessor.assess(
                Optional.of(profile(List.of(), List.of(
                        statistic("CUMULATIVE_ACTIVITY_START_COUNT", "24:00", 5, 0, 20, 20)))),
                new CurrentState(justAfterMidnight, false, quality, ActivityLedger.of(List.of(
                        use("KETTLE", yesterdayStart.plusHours(8), yesterdayStart.plusHours(8)
                                .plusMinutes(5))))),
                policy());

        IndicatorResult activity = indicator(assessment, "A");
        // 기준 시각은 오늘 자정, 곧 전날의 마지막 구간이다.
        assertThat(activity.bucket()).isEqualTo("24:00");
        // 전날 하루치 누적과 전날 하루치 분포를 비교한다.
        assertThat(activity.observed()).isEqualTo(1.0);
        assertThat(activity.score()).isEqualTo(0.5);
    }
}
