package com.nilm.monitoring;

import com.nilm.monitoring.dto.kafka.AnalysisEventMessage;
import com.nilm.monitoring.service.AnalysisEventService;
import com.nilm.monitoring.service.NotificationReady;
import com.nilm.monitoring.service.WebPushSender;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@RecordApplicationEvents
class AnalysisEventFlowTest {
    @Autowired AnalysisEventService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents applicationEvents;
    @Autowired ApplicationContext context;
    @Autowired KafkaListenerEndpointRegistry listeners;

    @BeforeEach
    void setup() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from subjects");
        jdbc.update("""
                insert into subjects(household_id, auth_sub, birth_date, name, phone, address)
                values ('house-test', 'test-subject-3', DATE '1950-01-01', 'test', '010', 'test')
                """);
        // 외부 푸시 없이 커밋 후 발송 이벤트 발행 여부만 확인한다.
        ReflectionTestUtils.setField(service, "pushEnabled", true);
        applicationEvents.clear();
    }

    AnalysisEventMessage message(int score) {
        return new AnalysisEventMessage(UUID.randomUUID(), "house-test", score,
                OffsetDateTime.now(ZoneOffset.UTC), Map.of("normal_days", 24), null, null);
    }

    @Test
    void registersKafkaListenerAndStartsWithoutVapidKeys() {
        assertThat(listeners.getListenerContainers()).hasSize(1);
        assertThat(context.getBeansOfType(WebPushSender.class)).isEmpty();
        assertThat(listeners.getListenerContainers()).allMatch(c -> !c.isRunning());
    }

    @Test
    void savesLegacyEventAndCreatesOneNotificationOnReplay() {
        var event = message(95);
        service.handle(event);
        service.handle(event);
        assertThat(jdbc.queryForObject("select count(*) from analysis_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select risk_level from analysis_events", String.class)).isEqualTo("DANGER");
        assertThat(jdbc.queryForObject("select appliance_type from analysis_events", String.class)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select auth_sub from notifications", String.class)).isEqualTo("test-subject-3");
        assertThat(jdbc.queryForObject("select state_version from subjects", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select current_risk_level from subjects", String.class)).isEqualTo("DANGER");
        assertThat(jdbc.queryForObject("select current_risk_score from subjects", Integer.class)).isEqualTo(95);
        assertThat(applicationEvents.stream(NotificationReady.class).count()).isEqualTo(1);
    }

    @Test
    void normalEventIsStoredWithoutNotification() {
        service.handle(message(69));
        assertThat(jdbc.queryForObject("select count(*) from analysis_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isZero();
    }

    @Test
    void warningBoundaryCreatesWarningNotification() {
        service.handle(message(70));
        assertThat(jdbc.queryForObject("select risk_level from analysis_events", String.class)).isEqualTo("WARNING");
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    void awayEventIsStoredWithoutNotification() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("update subjects set monitoring_enabled=false, away_started_at=?, away_until=?",
                now.minusHours(1), now.plusHours(1));
        service.handle(message(95));
        assertThat(jdbc.queryForObject("select count(*) from analysis_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isZero();
    }

    @Test
    void unknownHouseholdFailsRatherThanAcknowledgingMissingData() {
        jdbc.update("delete from subjects");
        assertThatThrownBy(() -> service.handle(message(95))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from analysis_events", Integer.class)).isZero();
    }

}

