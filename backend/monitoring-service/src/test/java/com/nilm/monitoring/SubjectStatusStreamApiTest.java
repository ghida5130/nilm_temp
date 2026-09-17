package com.nilm.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.dto.kafka.AnalysisEventMessage;
import com.nilm.monitoring.service.AnalysisEventService;
import com.nilm.monitoring.service.SubjectStatusStreamRegistry;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SubjectStatusStreamApiTest {

    private static final String ENDPOINT = "/api/monitoring/stream";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AnalysisEventService analysisEvents;
    @Autowired SubjectStatusStreamRegistry registry;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from subjects");
        jdbc.update("delete from managers");
    }

    @Test
    void pushesTheChangedSubjectToItsAssignedManager() throws Exception {
        long managerId = insertManager("manager-a");
        long subjectId = insertSubject(managerId, "H001", "subject-H001");

        MvcResult stream = openStream("manager-a");

        UUID eventId = UUID.randomUUID();
        analysisEvents.handle(new AnalysisEventMessage(
                eventId, "H001", 92, OffsetDateTime.now(ZoneOffset.UTC),
                Map.of("expected_until", "2026-09-16T08:10:00Z"),
                "ROUTINE_MISSED", "KETTLE"));

        JsonNode payload = firstSubjectStatus(stream);
        assertThat(payload.get("subjectId").asText()).isEqualTo(Long.toString(subjectId));
        assertThat(payload.get("version").asLong()).isEqualTo(2L);
        assertThat(payload.get("trigger").asText())
                .isEqualTo(StateChangeTrigger.DETECTION.name());
        assertThat(payload.get("riskLevel").asText()).isEqualTo("DANGER");
        assertThat(payload.get("riskScore").asInt()).isEqualTo(92);
        assertThat(payload.get("recentEventCount").asInt()).isEqualTo(1);
        assertThat(payload.get("lastActivity").get("applianceType").asText())
                .isEqualTo("KETTLE");
        assertThat(payload.get("unresolvedAlertCount").asInt()).isEqualTo(1);
        assertThat(payload.get("latestAlert").get("eventId").asText())
                .isEqualTo(eventId.toString());
        assertThat(payload.get("latestAlert").get("managerStatus").asText())
                .isEqualTo("UNCONFIRMED");
        assertThat(payload.get("latestAlert").get("subjectResponse").get("status").asText())
                .isEqualTo("PENDING");
        assertThat(payload.get("latestAlert").get("subjectResponse").get("answer").isNull())
                .isTrue();
        assertThat(payload.get("lastDetection").get("eventId").asText())
                .isEqualTo(eventId.toString());
        assertThat(payload.get("lastDetection").get("eventType").asText())
                .isEqualTo("ROUTINE_MISSED");
        assertThat(payload.get("lastDetection").get("description").asText())
                .isEqualTo("전기포트 미작동 감지");
        assertThat(payload.get("lastDetection").get("reason").get("expected_until").asText())
                .isEqualTo("2026-09-16T08:10:00Z");
        assertThat(payload.get("updatedAt").asText()).isNotBlank();
    }

    @Test
    void doesNotLeakOtherManagersSubjects() throws Exception {
        long managerId = insertManager("manager-a");
        insertManager("manager-b");
        insertSubject(managerId, "H001", "subject-H001");

        MvcResult other = openStream("manager-b");

        analysisEvents.handle(new AnalysisEventMessage(
                UUID.randomUUID(), "H001", 95, OffsetDateTime.now(ZoneOffset.UTC),
                Map.of("normal_days", 24), "ROUTINE_MISSED", "KETTLE"));

        assertThat(other.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("subject-status");
    }

    @Test
    void pushesTheSubjectAnswerToTheManager() throws Exception {
        long managerId = insertManager("manager-a");
        insertSubject(managerId, "H001", "subject-H001");

        analysisEvents.handle(new AnalysisEventMessage(
                UUID.randomUUID(), "H001", 95, OffsetDateTime.now(ZoneOffset.UTC),
                Map.of("normal_days", 24), "ROUTINE_MISSED", "KETTLE"));

        // 알림이 생긴 뒤에 붙은 담당자도 이후의 응답은 바로 받아야 한다.
        MvcResult stream = openStream("manager-a");
        long alertId = jdbc.queryForObject("select id from notifications", Long.class);

        mockMvc.perform(put("/api/monitoring/notifications/{id}/responses", alertId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"yes\",\"source\":\"user\","
                                + "\"respondedAt\":\"2026-09-16T09:00:25Z\"}"))
                .andExpect(status().isOk());

        JsonNode payload = firstSubjectStatus(stream);
        assertThat(payload.get("trigger").asText())
                .isEqualTo(StateChangeTrigger.SUBJECT_RESPONSE.name());
        assertThat(payload.get("latestAlert").get("subjectResponse").get("status").asText())
                .isEqualTo("ANSWERED");
        assertThat(payload.get("latestAlert").get("subjectResponse").get("answer").asText())
                .isEqualTo("YES");
    }

    @Test
    void rejectsAnonymousAndNonManagerTokens() throws Exception {
        long managerId = insertManager("manager-a");

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ENDPOINT)
                        .with(jwt().jwt(token -> token.subject("not-a-manager"))))
                .andExpect(status().isForbidden());

        assertThat(registry.hasSubscriber(managerId)).isFalse();
    }

    private MvcResult openStream(String managerAuthSub) throws Exception {
        MvcResult result = mockMvc.perform(get(ENDPOINT)
                        .with(jwt().jwt(token -> token.subject(managerAuthSub))))
                .andExpect(request().asyncStarted())
                .andReturn();
        long managerId = jdbc.queryForObject(
                "select id from managers where auth_sub = ?", Long.class, managerAuthSub);
        assertThat(registry.connectionCount(managerId)).isEqualTo(1);
        return result;
    }

    /** SSE 본문에서 첫 subject-status 이벤트의 data 한 줄을 꺼낸다. */
    private JsonNode firstSubjectStatus(MvcResult stream) throws Exception {
        // SSE 본문은 UTF-8이고, 목 응답은 지정하지 않으면 ISO-8859-1로 읽는다.
        String body = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:subject-status");
        String data = body.lines()
                .filter(line -> line.startsWith("data:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("subject-status 본문이 없습니다: " + body))
                .substring("data:".length());
        return objectMapper.readTree(data);
    }

    private long insertManager(String authSub) {
        jdbc.update("""
                insert into managers(auth_sub, name, organization)
                values (?, '김담당', '중구 복지관')
                """, authSub);
        return jdbc.queryForObject(
                "select id from managers where auth_sub = ?", Long.class, authSub);
    }

    private long insertSubject(long managerId, String householdId, String authSub) {
        jdbc.update("""
                insert into subjects(
                    household_id, auth_sub, birth_date, name, phone, address, manager_id
                ) values (?, ?, cast('1950-01-01' as date), '김철수', '01012345678',
                          '서울특별시 중구', ?)
                """, householdId, authSub, managerId);
        return jdbc.queryForObject(
                "select id from subjects where household_id = ?", Long.class, householdId);
    }
}
