package com.nilm.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.dto.kafka.AnalysisSnapshotMessage;
import com.nilm.monitoring.service.ApplianceActivityService;
import com.nilm.monitoring.service.SubjectStateChanged;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@RecordApplicationEvents
class ApplianceActivityFlowTest {

    @Autowired ApplianceActivityService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents applicationEvents;

    private OffsetDateTime baseTime;

    @BeforeEach
    void setup() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from appliance_states");
        jdbc.update("delete from subjects");
        jdbc.update("""
                insert into subjects(household_id, auth_sub, birth_date, name, phone, address)
                values ('H001', 'test-subject-3', DATE '1950-01-01', 'test', '010', 'test')
                """);
        baseTime = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        applicationEvents.clear();
    }

    private AnalysisSnapshotMessage snapshot(
            String householdId,
            OffsetDateTime observedAt,
            String onAppliance
    ) {
        List<AnalysisSnapshotMessage.Appliance> appliances = List.of(
                new AnalysisSnapshotMessage.Appliance("KETTLE", "KETTLE".equals(onAppliance)),
                new AnalysisSnapshotMessage.Appliance("INDUCTION", "INDUCTION".equals(onAppliance)),
                new AnalysisSnapshotMessage.Appliance("IRON", "IRON".equals(onAppliance)),
                new AnalysisSnapshotMessage.Appliance("MICROWAVE", "MICROWAVE".equals(onAppliance)),
                new AnalysisSnapshotMessage.Appliance("HAIR_DRYER", "HAIR_DRYER".equals(onAppliance)),
                new AnalysisSnapshotMessage.Appliance("VACUUM_CLEANER",
                        "VACUUM_CLEANER".equals(onAppliance))
        );
        return new AnalysisSnapshotMessage(
                1,
                UUID.randomUUID().toString(),
                householdId,
                observedAt,
                observedAt.plusNanos(125_000_000L),
                new AnalysisSnapshotMessage.Measurement(null, null, null, null),
                appliances
        );
    }

    private OffsetDateTime lastActivityAt() {
        return jdbc.queryForObject(
                "select last_activity_at from subjects", OffsetDateTime.class);
    }

    private String lastActivityAppliance() {
        return jdbc.queryForObject(
                "select last_activity_appliance from subjects", String.class);
    }

    @Test
    void firstSnapshotOnlySeedsStateWithoutRecordingActivity() {
        service.handle(snapshot("H001", baseTime, "MICROWAVE"));

        assertThat(jdbc.queryForObject(
                "select count(*) from appliance_states", Integer.class)).isEqualTo(6);
        assertThat(lastActivityAt()).isNull();
        assertThat(applicationEvents.stream(SubjectStateChanged.class).count()).isZero();
    }

    @Test
    void onToOffTransitionRecordsLastActivity() {
        service.handle(snapshot("H001", baseTime, "MICROWAVE"));
        service.handle(snapshot("H001", baseTime.plusMinutes(10), null));

        assertThat(lastActivityAppliance()).isEqualTo("MICROWAVE");
        assertThat(lastActivityAt()).isEqualTo(baseTime.plusMinutes(10));
        // 전환이 있었으므로 위험 평가도 같은 트랜잭션에서 함께 돈다.
        assertThat(applicationEvents.stream(SubjectStateChanged.class)
                .map(SubjectStateChanged::trigger))
                .contains(StateChangeTrigger.ACTIVITY);
    }

    @Test
    void offToOnTransitionDoesNotRecordActivity() {
        service.handle(snapshot("H001", baseTime, null));
        service.handle(snapshot("H001", baseTime.plusMinutes(10), "KETTLE"));

        assertThat(lastActivityAt()).isNull();
        assertThat(jdbc.queryForObject(
                "select is_on from appliance_states where appliance_type='KETTLE'",
                Boolean.class)).isTrue();
    }

    @Test
    void unchangedSnapshotKeepsPreviousActivity() {
        service.handle(snapshot("H001", baseTime, "KETTLE"));
        service.handle(snapshot("H001", baseTime.plusMinutes(10), null));
        applicationEvents.clear();

        service.handle(snapshot("H001", baseTime.plusMinutes(20), null));

        assertThat(lastActivityAt()).isEqualTo(baseTime.plusMinutes(10));
        assertThat(applicationEvents.stream(SubjectStateChanged.class).count()).isZero();
    }

    @Test
    void lateSnapshotDoesNotRollBackLastActivity() {
        service.handle(snapshot("H001", baseTime, "KETTLE"));
        service.handle(snapshot("H001", baseTime.plusMinutes(10), null));

        // 이미 반영한 전환보다 앞선 스냅샷이 뒤늦게 도착한다.
        service.handle(snapshot("H001", baseTime.plusMinutes(5), "IRON"));

        assertThat(lastActivityAppliance()).isEqualTo("KETTLE");
        assertThat(lastActivityAt()).isEqualTo(baseTime.plusMinutes(10));
        assertThat(jdbc.queryForObject(
                "select is_on from appliance_states where appliance_type='IRON'",
                Boolean.class)).isFalse();
    }

    @Test
    void replayedSnapshotIsIdempotent() {
        service.handle(snapshot("H001", baseTime, "KETTLE"));
        var off = snapshot("H001", baseTime.plusMinutes(10), null);
        service.handle(off);
        applicationEvents.clear();

        service.handle(off);

        assertThat(lastActivityAt()).isEqualTo(baseTime.plusMinutes(10));
        assertThat(applicationEvents.stream(SubjectStateChanged.class).count()).isZero();
    }

    @Test
    void snapshotOfUnknownHouseholdIsIgnored() {
        service.handle(snapshot("H999", baseTime, "KETTLE"));

        assertThat(jdbc.queryForObject(
                "select count(*) from appliance_states", Integer.class)).isZero();
    }
}
