package com.nilm.monitoring;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MyDashboardApiTest {

    private static final String ENDPOINT = "/api/monitoring/my-dashboard";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from subjects");
        jdbc.update("delete from managers");
    }

    @Test
    void returnsSubjectAwayModeAndAssignedManager() throws Exception {
        jdbc.update("""
                insert into managers(auth_sub, name, organization, phone)
                values ('manager-a', '이담당', '중구 복지관', '010-1234-5678')
                """);
        long managerId = jdbc.queryForObject(
                "select id from managers where auth_sub = 'manager-a'", Long.class);
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-09-17T00:00:00Z");
        OffsetDateTime until = OffsetDateTime.parse("2026-09-17T09:00:00Z");
        jdbc.update("""
                insert into subjects(
                    household_id, auth_sub, birth_date, name, phone, address,
                    monitoring_enabled, away_started_at, away_until, manager_id
                ) values ('H001', 'subject-a', cast('1948-01-01' as date), '김철수',
                    '010-9999-9999', '서울특별시', false, ?, ?, ?)
                """, startedAt, until, managerId);
        long subjectId = jdbc.queryForObject(
                "select id from subjects where auth_sub = 'subject-a'", Long.class);

        mockMvc.perform(get(ENDPOINT)
                        .with(jwt().jwt(token -> token.subject("subject-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value(Long.toString(subjectId)))
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.awayMode.enabled").value(true))
                .andExpect(jsonPath("$.awayMode.startedAt")
                        .value("2026-09-17T00:00:00Z"))
                .andExpect(jsonPath("$.awayMode.until")
                        .value("2026-09-17T09:00:00Z"))
                .andExpect(jsonPath("$.manager.name").value("이담당"))
                .andExpect(jsonPath("$.manager.phone").value("010-1234-5678"));
    }

    @Test
    void inactiveAwayModeReturnsDisabledWithNullTimes() throws Exception {
        jdbc.update("""
                insert into subjects(
                    household_id, auth_sub, birth_date, name, phone, address,
                    monitoring_enabled
                ) values ('H001', 'subject-a', cast('1948-01-01' as date), '김철수',
                    '010-9999-9999', '서울특별시', true)
                """);

        mockMvc.perform(get(ENDPOINT)
                        .with(jwt().jwt(token -> token.subject("subject-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awayMode.enabled").value(false))
                .andExpect(jsonPath("$.awayMode.startedAt").doesNotExist())
                .andExpect(jsonPath("$.awayMode.until").doesNotExist())
                .andExpect(jsonPath("$.manager").doesNotExist());
    }

    @Test
    void unauthenticatedAndUnknownSubjectRequestsAreRejected() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ENDPOINT)
                        .with(jwt().jwt(token -> token.subject("unknown-subject"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("대상자 정보를 찾을 수 없습니다."));
    }
}
