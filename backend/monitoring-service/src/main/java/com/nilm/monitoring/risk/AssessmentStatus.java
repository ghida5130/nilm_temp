package com.nilm.monitoring.risk;

/**
 * 평가 한 번의 성립 여부. 설계 11.1절의 평가 자격 표를 그대로 옮긴 값이다.
 *
 * <p>평가 불가를 0점·정상으로 표시하지 않기 위해 점수·등급과 분리해서 내보낸다.
 */
public enum AssessmentStatus {

    /** 필요한 지표를 계산했고 등급까지 낼 수 있다. */
    VALID,

    /** 일부 지표만 계산했다. 누락 지표를 0으로 채우지 않으므로 등급은 내지 않는다. */
    PARTIAL,

    /** 비교할 프로필이 아직 없다. 학습 기간이다. */
    LEARNING,

    /** 프로필이 유효기간을 넘겨 비교 기준으로 쓸 수 없다. */
    INSUFFICIENT_DATA
}
