package com.nilm.monitoring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Flyway 이후, Kafka 리스너 시작 전에 개발용 대상자를 등록한다.
 * 기존 가구 또는 사용자 데이터는 수정하지 않는다.
 */
@Slf4j
@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(
        name = "app.test-subject.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LocalSubjectInitializer implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;
    private final String householdId;
    private final String authSub;

    public LocalSubjectInitializer(
            JdbcTemplate jdbc,
            @Value("${app.test-subject.household-id:HOUSE001}") String householdId,
            @Value("${app.push.test-auth-sub}") String authSub
    ) {
        this.jdbc = jdbc;
        this.householdId = householdId;
        this.authSub = authSub;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!StringUtils.hasText(householdId) || !StringUtils.hasText(authSub)) {
            throw new IllegalArgumentException("테스트 대상자의 가구 ID와 auth_sub가 필요합니다.");
        }
        try {
            int inserted = jdbc.update("""
                    INSERT INTO subjects (
                        household_id, auth_sub, birth_date, name, phone, address,
                        monitoring_enabled, manager_memo
                    )
                    SELECT ?, ?, DATE '1950-01-01', '테스트 대상자',
                           '010-0000-0000', '테스트 주소', TRUE, '로컬 자동 생성 테스트 데이터'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM subjects WHERE household_id = ? OR auth_sub = ?
                    )
                    """, householdId, authSub, householdId, authSub);
            if (inserted == 1) {
                log.info("로컬 테스트 대상자 등록 완료: householdId={}", householdId);
            } else {
                log.info("기존 가구/사용자가 있어 테스트 대상자 등록 생략: householdId={}. 기존 auth_sub 연결을 확인하세요.",
                        householdId);
            }
        } catch (DuplicateKeyException e) {
            // 같은 설정으로 여러 로컬 인스턴스가 시작되어도 기존 행을 덮어쓰지 않는다.
            log.info("테스트 사용자가 이미 등록되어 있습니다: householdId={}", householdId);
        }
    }
}
