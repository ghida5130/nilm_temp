package com.nilm.monitoring.risk;

import com.nilm.monitoring.config.enums.RiskLevel;
import com.nilm.monitoring.service.ResolvedProfile;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 가구 프로필과 현재 상태를 비교해 위험 점수를 내는 순수 계산기.
 *
 * <p>Spring도 JPA도 쓰지 않는다. 입력으로 받은 값만 보고 같은 입력에는 언제나 같은 답을 낸다.
 * 점수식은 설계 11.2절의 "미검증 정책안"이고, 임계·유예·변동폭은 모두 {@link RiskPolicy}에서 온다.
 *
 * <p>장시간 사용 지표 U는 여기서 계산하지 않는다. 설계 11.4절대로 그 판단의 소유자는
 * 분석 서비스다. 모니터링은 스냅샷의 ON/OFF 상태만 갖고 있어 사용 세션의 시작 시각을
 * 분석 서비스만큼 정확히 알지 못하고, 같은 사건을 두 주체가 각자 점수화하면
 * 최종 점수의 주체가 둘로 갈린다. 수신한 PROLONGED_APPLIANCE_USE는 점수식에 넣지 않고
 * 이벤트 등급 슬롯으로만 반영한다. U 소유권을 넘겨받을 때 11.2절의
 * max(B,U) + 0.1 x min(B,U) 결합을 다시 적용한다.
 */
public class RiskAssessor {

    /** 점수식의 판. 지표 구성이 바뀌면 이 값을 올려 과거 이력과 구분한다. */
    public static final String SCORE_VERSION = "monitoring-score-v1-MIA";

    /** 영업일과 시간대 구간의 기준. 프로필을 만든 배치와 같은 시간대를 쓴다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 프로필의 시간대 구간 폭(분). */
    private static final int BUCKET_MINUTES = 30;

    private static final String METRIC_INACTIVITY = "INACTIVITY_ELAPSED";
    private static final String METRIC_ACTIVITY = "CUMULATIVE_ACTIVITY_START_COUNT";

    /** MAD를 표준편차 눈금으로 옮기는 상수. */
    private static final double MAD_TO_SIGMA = 1.4826;

    /**
     * 활동 감소(A)의 최소 변동폭. 정책의 minSpreadSeconds는 단위가 초라서
     * 횟수를 다루는 이 지표에 그대로 쓰면 분모가 과하게 커져 신호가 사라진다.
     * 횟수의 최소 변동폭은 1회로 고정한다.
     */
    private static final double MIN_SPREAD_COUNT = 1.0;

    private static final String QUALITY_READY = "READY";
    private static final String SCOPE_OVERALL = "OVERALL";
    private static final String SCOPE_WEEKDAY = "WEEKDAY";
    private static final String GROUP_ALL = "ALL";

    private static final String REASON_AWAY = "AWAY";
    private static final String REASON_OBSERVATION_STALE = "OBSERVATION_STALE";
    private static final String REASON_NO_BASELINE = "NO_BASELINE";
    private static final String REASON_NO_STATISTIC = "NO_STATISTIC";
    private static final String REASON_INSUFFICIENT_SAMPLE = "INSUFFICIENT_SAMPLE";
    private static final String REASON_NO_ACTIVITY_HISTORY = "NO_ACTIVITY_HISTORY";

    /**
     * 평가 한 번.
     *
     * @param profile 이번 평가가 쓸 프로필. 비어 있으면 아직 학습 기간이다
     * @param state 평가 시작 시점에 고정한 현재 상태
     */
    public RiskAssessment assess(
            Optional<ResolvedProfile> profile,
            CurrentState state,
            RiskPolicy policy
    ) {
        if (profile.isEmpty()) {
            return unavailable(AssessmentStatus.LEARNING, null, policy);
        }
        ResolvedProfile resolved = profile.get();
        if (resolved.stale()) {
            // 유효기간을 넘긴 프로필로 비교하면 근거 없는 점수가 나온다.
            return unavailable(
                    AssessmentStatus.INSUFFICIENT_DATA, resolved.profileVersion(), policy);
        }

        // 스냅샷이 끊긴 동안의 "무활동"은 대상자가 가만히 있었다는 뜻이 아니라
        // 우리가 보지 못했다는 뜻이다. 현재 값을 쓰는 지표를 아예 계산하지 않는다.
        boolean observationStale = isObservationStale(state, policy);

        List<Computed> computed = new ArrayList<>();
        computed.add(assessRoutineMissed(resolved, state, policy));
        computed.add(assessInactivity(resolved, state, policy, observationStale));
        computed.add(assessActivityDrop(resolved, state, policy, observationStale));

        List<IndicatorResult> indicators = computed.stream().map(Computed::result).toList();
        List<Computed> used = computed.stream().filter(c -> c.result().included()).toList();

        if (used.isEmpty()) {
            // 누락 지표를 0으로 채우면 데이터 없음이 "정상"으로 보인다. 점수를 내지 않는다.
            return new RiskAssessment(
                    null, null, AssessmentStatus.PARTIAL, 0.0, indicators,
                    resolved.profileVersion(), policy.policyVersion(), SCORE_VERSION);
        }

        double base = used.stream().mapToDouble(c -> c.result().score()).max().orElse(0.0);
        int score = (int) Math.round(100 * base);

        // 신뢰도는 점수에 곱하지 않는다. 데이터 부족을 낮은 위험으로 바꾸지 않기 위해서다.
        double confidence = used.stream()
                .mapToDouble(c ->
                        Math.min(1.0, (double) c.eligibleDays() / policy.minEligibleDays()))
                .min()
                .orElse(0.0);

        // 부재를 보는 두 축(M·I)이 모두 빠졌으면 전체 정상 판단과 구분해야 한다.
        boolean coreAvailable = used.stream()
                .anyMatch(c -> "M".equals(c.result().code()) || "I".equals(c.result().code()));
        if (!coreAvailable) {
            return new RiskAssessment(
                    score, null, AssessmentStatus.PARTIAL, confidence, indicators,
                    resolved.profileVersion(), policy.policyVersion(), SCORE_VERSION);
        }

        return new RiskAssessment(
                score, levelOf(score, policy), AssessmentStatus.VALID, confidence, indicators,
                resolved.profileVersion(), policy.policyVersion(), SCORE_VERSION);
    }

    /** 점수로 등급을 가른다. 히스테리시스는 이 결과를 받는 반영 단계가 건다. */
    public static RiskLevel levelOf(int score, RiskPolicy policy) {
        if (score >= policy.dangerThreshold()) {
            return RiskLevel.DANGER;
        }
        if (score >= policy.warningThreshold()) {
            return RiskLevel.WARNING;
        }
        return RiskLevel.NORMAL;
    }

    /**
     * 루틴 미사용 M.
     *
     * <p>가전마다 "평소 이 시각까지는 썼다"는 기준선과 오늘 사용 사실을 비교하고,
     * 가장 높은 점수를 M으로 삼는다. 어느 가전이 M을 만들었는지는 관측값(현재 초)과
     * 비교 기준(그 가전의 첫 사용 P90)으로 되짚는다.
     */
    private Computed assessRoutineMissed(
            ResolvedProfile profile,
            CurrentState state,
            RiskPolicy policy
    ) {
        if (state.away()) {
            // 집에 없으면 가전을 쓰지 않는 것이 정상이다.
            return Computed.excluded("M", REASON_AWAY);
        }

        ZonedDateTime local = state.now().atZoneSameInstant(ZONE);
        int nowSecond = local.toLocalTime().toSecondOfDay();
        String weekday = local.getDayOfWeek().name().substring(0, 3);

        double best = -1;
        double bestCenter = 0;
        double bestSpread = 0;
        long bestSampleDays = 0;

        for (String applianceType : appliances(profile)) {
            ResolvedProfile.RoutineBaseline baseline =
                    pickBaseline(profile, applianceType, weekday, policy);
            if (baseline == null || baseline.expectedUntilSecond() == null) {
                continue;
            }

            // 배치의 expected_until_second가 곧 첫 사용 시각의 P90이다.
            double p90 = baseline.expectedUntilSecond();
            double spread = spreadSeconds(baseline, policy);
            double score;
            CurrentState.DailyUsage usage = state.todayUsage().get(applianceType);
            if (usage != null && usage.firstOnAt() != null) {
                // 오늘 이미 썼으면 미사용이 아니다.
                score = 0;
            } else if (nowSecond < p90) {
                // 평소 쓰던 시각이 아직 지나지 않았다.
                score = 0;
            } else {
                double delay = clip((nowSecond - p90) / spread);
                score = probability(baseline) * delay;
            }

            if (score > best) {
                best = score;
                bestCenter = p90;
                bestSpread = spread;
                bestSampleDays = baseline.sampleDays();
            }
        }

        if (best < 0) {
            return Computed.excluded("M", REASON_NO_BASELINE);
        }
        return new Computed(
                new IndicatorResult(
                        "M", best, (double) nowSecond, bestCenter, bestSpread, null, null),
                bestSampleDays);
    }

    /**
     * 무활동 I.
     *
     * <p>현재 경과시간을 과거 같은 시간대의 경과시간 분포와 비교한다.
     * 완료된 무활동 구간의 길이 분포와 섞지 않는다(설계 10장).
     */
    private Computed assessInactivity(
            ResolvedProfile profile,
            CurrentState state,
            RiskPolicy policy,
            boolean observationStale
    ) {
        if (state.away()) {
            return Computed.excluded("I", REASON_AWAY);
        }
        if (observationStale) {
            return Computed.excluded("I", REASON_OBSERVATION_STALE);
        }
        if (state.lastActivityEndedAt() == null) {
            // 비교할 기준점이 없다. 경과시간을 0으로도 무한으로도 볼 수 없다.
            return Computed.excluded("I", REASON_NO_ACTIVITY_HISTORY);
        }

        double observed = state.anyApplianceOn()
                ? 0
                : state.now().toEpochSecond() - state.lastActivityEndedAt().toEpochSecond();

        return compare("I", profile, state, policy, METRIC_INACTIVITY,
                observed, policy.minSpreadSeconds(), false);
    }

    /**
     * 활동 감소 A.
     *
     * <p>오늘 지금까지의 가전 시작 횟수를 과거 같은 시각까지의 누적 분포와 비교한다.
     * 현재 10시까지의 활동을 과거 하루 전체와 비교하지 않으려고 시간대 구간을 맞춘다.
     */
    private Computed assessActivityDrop(
            ResolvedProfile profile,
            CurrentState state,
            RiskPolicy policy,
            boolean observationStale
    ) {
        if (state.away()) {
            return Computed.excluded("A", REASON_AWAY);
        }
        if (observationStale) {
            return Computed.excluded("A", REASON_OBSERVATION_STALE);
        }

        double observed = state.todayUsage().values().stream()
                .mapToInt(CurrentState.DailyUsage::startCount)
                .sum();

        // 활동이 적을수록 위험하므로 부호가 반대다.
        return compare("A", profile, state, policy, METRIC_ACTIVITY,
                observed, MIN_SPREAD_COUNT, true);
    }

    /** 현재 값 하나를 시간대별 통계 한 줄과 비교해 0~1 점수로 옮긴다. */
    private Computed compare(
            String code,
            ResolvedProfile profile,
            CurrentState state,
            RiskPolicy policy,
            String metricName,
            double observed,
            double minSpread,
            boolean lowerIsWorse
    ) {
        ResolvedProfile.Statistic statistic = lookup(profile, metricName, state.now());
        if (statistic == null || statistic.p50() == null) {
            return Computed.excluded(code, REASON_NO_STATISTIC);
        }
        if (statistic.sampleCount() < policy.minSampleCount()
                || !QUALITY_READY.equals(statistic.qualityStatus())) {
            return Computed.excluded(code, REASON_INSUFFICIENT_SAMPLE);
        }

        double center = statistic.p50();
        double mad = statistic.mad() == null ? 0 : statistic.mad();
        // 표본이 작거나 MAD가 0이어도 분모가 0이 되지 않게 바닥을 깐다.
        double spread = Math.max(MAD_TO_SIGMA * mad, minSpread);
        double z = Math.max(0, (lowerIsWorse ? center - observed : observed - center) / spread);
        double score = clip((z - policy.zLow()) / (policy.zHigh() - policy.zLow()));

        String bucket = statistic.timeBucket() == null
                ? timeBucket(state.now())
                : statistic.timeBucket();
        return new Computed(
                new IndicatorResult(code, score, observed, center, spread, bucket, null),
                statistic.eligibleDayCount());
    }

    /**
     * 비교할 통계 한 줄을 찾는다.
     *
     * <p>요일 그룹은 좁은 조건부터 넓은 조건으로 완화한다(설계 10장).
     * 시간대 구간은 배치가 쓰는 표기 하나만 본다. 표기가 어긋나면 조용히 다른 구간을
     * 집어오는 대신 지표를 제외하는 편이 낫다.
     */
    private ResolvedProfile.Statistic lookup(
            ResolvedProfile profile,
            String metricName,
            OffsetDateTime now
    ) {
        String bucket = timeBucket(now);
        for (String group : List.of(weekdayGroup(now), GROUP_ALL)) {
            Optional<ResolvedProfile.Statistic> found =
                    profile.statistic(metricName, null, group, bucket);
            if (found.isPresent()) {
                return found.get();
            }
        }
        return null;
    }

    /**
     * 가전의 기준선을 고른다. 요일 기준선의 표본이 모자라면 전체 기준선으로 완화한다.
     * 배치가 쓰지 말라고 한 기준선(enabled=false 또는 품질 미달)은 아예 보지 않는다.
     */
    private ResolvedProfile.RoutineBaseline pickBaseline(
            ResolvedProfile profile,
            String applianceType,
            String weekday,
            RiskPolicy policy
    ) {
        ResolvedProfile.RoutineBaseline overall = null;
        ResolvedProfile.RoutineBaseline weekdayRow = null;
        for (ResolvedProfile.RoutineBaseline baseline : profile.baselines()) {
            if (!applianceType.equals(baseline.applianceType())
                    || !baseline.enabled()
                    || !QUALITY_READY.equals(baseline.qualityStatus())) {
                continue;
            }
            if (SCOPE_OVERALL.equals(baseline.baselineScope())) {
                overall = baseline;
            } else if (SCOPE_WEEKDAY.equals(baseline.baselineScope())
                    && weekday.equalsIgnoreCase(baseline.weekday())) {
                weekdayRow = baseline;
            }
        }
        if (weekdayRow != null && weekdayRow.sampleDays() >= policy.minSampleCount()) {
            return weekdayRow;
        }
        return overall;
    }

    private List<String> appliances(ResolvedProfile profile) {
        return profile.baselines().stream()
                .map(ResolvedProfile.RoutineBaseline::applianceType)
                .distinct()
                .toList();
    }

    /** 첫 사용 P90과 P50의 간격. 너무 좁으면 조금만 지나도 1점이 되므로 바닥을 깐다. */
    private double spreadSeconds(ResolvedProfile.RoutineBaseline baseline, RiskPolicy policy) {
        double grace = policy.minGrace().getSeconds();
        if (baseline.firstUseTimeP50Second() == null) {
            return grace;
        }
        return Math.max(
                baseline.expectedUntilSecond() - (double) baseline.firstUseTimeP50Second(),
                grace);
    }

    private double probability(ResolvedProfile.RoutineBaseline baseline) {
        return baseline.dailyUseProbability() == null
                ? 0
                : baseline.dailyUseProbability().doubleValue();
    }

    private boolean isObservationStale(CurrentState state, RiskPolicy policy) {
        if (state.lastObservedAt() == null) {
            return true;
        }
        return state.now().toEpochSecond() - state.lastObservedAt().toEpochSecond()
                > policy.observationMaxAge().getSeconds();
    }

    private String weekdayGroup(OffsetDateTime now) {
        DayOfWeek day = now.atZoneSameInstant(ZONE).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? "WEEKEND" : "WEEKDAY";
    }

    /**
     * 시간대 구간 이름. 배치가 쓰는 표기를 그대로 따라 구간의 <b>끝 시각</b>만 적는다.
     * 12:00~12:30 구간은 "12:30"이고, 하루의 마지막 구간은 "24:00"이다.
     */
    private String timeBucket(OffsetDateTime now) {
        return formatMinutes((bucketIndex(now) + 1) * BUCKET_MINUTES);
    }

    private int bucketIndex(OffsetDateTime now) {
        int minuteOfDay = now.atZoneSameInstant(ZONE).toLocalTime().toSecondOfDay() / 60;
        return minuteOfDay / BUCKET_MINUTES;
    }

    private String formatMinutes(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private static double clip(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 1);
    }

    private RiskAssessment unavailable(
            AssessmentStatus status,
            String profileVersion,
            RiskPolicy policy
    ) {
        return new RiskAssessment(
                null, null, status, 0.0, List.of(),
                profileVersion, policy.policyVersion(), SCORE_VERSION);
    }

    /**
     * 지표 하나의 계산 결과와, 신뢰도를 매기는 데 쓸 관측일 수.
     * 관측일 수는 이력에 남기지 않으므로 결과 레코드 밖에 둔다.
     */
    private record Computed(IndicatorResult result, long eligibleDays) {

        static Computed excluded(String code, String reason) {
            return new Computed(IndicatorResult.excluded(code, reason), 0);
        }
    }
}
