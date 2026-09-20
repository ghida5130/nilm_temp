package com.nilm.monitoring.risk;

import com.nilm.monitoring.config.enums.RiskLevel;
import java.util.List;

/**
 * 평가 한 번의 결과.
 *
 * <p>점수·등급은 nullable이다. 설계 11.1절대로 "계산하지 못했다"를 0점·정상으로 바꾸지 않는다.
 *
 * @param confidence 0~1. 점수에 곱하지 않는다. 데이터 부족을 낮은 위험으로 둔갑시키지 않기 위해서다
 */
public record RiskAssessment(
        Integer score,
        RiskLevel level,
        AssessmentStatus status,
        double confidence,
        List<IndicatorResult> indicators,
        String profileVersion,
        String policyVersion,
        String scoreVersion
) {
}
