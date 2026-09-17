package com.nilm.monitoring;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SubjectPowerUsageApiTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String ENDPOINT = "/api/monitoring/subjects/{id}/power-usage";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from hourly_appliance_power_usage");
        jdbc.update("delete from hourly_power_usage");
        jdbc.update("delete from notifications");
        jdbc.update("delete from analysis_events");
        jdbc.update("delete from subjects");
        jdbc.update("delete from managers");
    }

    @Test
    void returnsTwentyFourCompleteBucketsForAPastDay() throws Exception {
        long managerId = insertManager("manager-a");
        long subjectId = insertSubject(managerId, "H001", "subject-H001");
        LocalDate date = LocalDate.now(SEOUL).minusDays(1);
        insertTotal("H001", date, 1, "180.5");
        insertTotal("H001", date, 2, "120.0");
        insertAppliance("H001", date, 1, "KETTLE", "60.0");
        insertAppliance("H001", date, 1, "MICROWAVE", "20.25");

        mockMvc.perform(get(ENDPOINT, subjectId)
                        .param("date", date.toString())
                        .with(jwt().jwt(token -> token.subject("manager-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value(Long.toString(subjectId)))
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.intervalMinutes").value(60))
                .andExpect(jsonPath("$.unit").value("Wh"))
                .andExpect(jsonPath("$.totalUsage").value(300.5))
                .andExpect(jsonPath("$.hourlyUsage.length()").value(24))
                .andExpect(jsonPath("$.hourlyUsage[0].hour").value(0))
                .andExpect(jsonPath("$.hourlyUsage[0].usage").value(0.0))
                .andExpect(jsonPath("$.hourlyUsage[0].status").value("COMPLETE"))
                .andExpect(jsonPath("$.hourlyUsage[1].usage").value(180.5))
                .andExpect(jsonPath("$.hourlyUsage[2].usage").value(120.0))
                .andExpect(jsonPath("$.hourlyUsage[23].status").value("COMPLETE"))
                .andExpect(jsonPath("$.appliances.length()").value(2))
                .andExpect(jsonPath("$.appliances[0].applianceType").value("KETTLE"))
                .andExpect(jsonPath("$.appliances[0].totalUsage").value(60.0))
                .andExpect(jsonPath("$.appliances[0].hourlyUsage.length()").value(24))
                .andExpect(jsonPath("$.appliances[0].hourlyUsage[1].usage").value(60.0))
                .andExpect(jsonPath("$.appliances[1].applianceType").value("MICROWAVE"))
                .andExpect(jsonPath("$.appliances[1].totalUsage").value(20.3))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void marksTheCurrentHourPartialAndLaterHoursNotYet() throws Exception {
        long managerId = insertManager("manager-a");
        long subjectId = insertSubject(managerId, "H001", "subject-H001");
        LocalDate today = LocalDate.now(SEOUL);
        int currentHour = OffsetDateTime.now().atZoneSameInstant(SEOUL).getHour();

        var result = mockMvc.perform(get(ENDPOINT, subjectId)
                        .with(jwt().jwt(token -> token.subject("manager-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.hourlyUsage.length()").value(24))
                .andExpect(jsonPath("$.hourlyUsage[" + currentHour + "].status")
                        .value("PARTIAL"))
                .andExpect(jsonPath("$.appliances.length()").value(0))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        if (currentHour < 23) {
            result.andExpect(jsonPath("$.hourlyUsage[23].status").value("NOT_YET"))
                    .andExpect(jsonPath("$.hourlyUsage[23].usage").doesNotExist());
        }
        if (currentHour > 0) {
            result.andExpect(jsonPath("$.hourlyUsage[0].status").value("COMPLETE"));
        }
    }

    @Test
    void rejectsOtherManagersAndUnknownSubjects() throws Exception {
        long managerId = insertManager("manager-a");
        insertManager("manager-b");
        long subjectId = insertSubject(managerId, "H001", "subject-H001");

        mockMvc.perform(get(ENDPOINT, subjectId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ENDPOINT, subjectId)
                        .with(jwt().jwt(token -> token.subject("manager-b"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ENDPOINT, subjectId + 999)
                        .with(jwt().jwt(token -> token.subject("manager-a"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ENDPOINT, subjectId)
                        .param("date", "2026-13-40")
                        .with(jwt().jwt(token -> token.subject("manager-a"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subjectCanReadTheirOwnGraph() throws Exception {
        long managerId = insertManager("manager-a");
        long subjectId = insertSubject(managerId, "H001", "subject-H001");

        mockMvc.perform(get(ENDPOINT, subjectId)
                        .with(jwt().jwt(token -> token.subject("subject-H001"))))
                .andExpect(status().isOk());
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

    private void insertTotal(String householdId, LocalDate date, int hour, String energyWh) {
        jdbc.update("""
                insert into hourly_power_usage(
                    household_id, bucket_start_at, energy_wh, updated_at
                ) values (?, ?, cast(? as numeric), ?)
                """,
                householdId,
                bucketStart(date, hour),
                energyWh,
                OffsetDateTime.now());
    }

    private void insertAppliance(
            String householdId,
            LocalDate date,
            int hour,
            String applianceType,
            String energyWh
    ) {
        jdbc.update("""
                insert into hourly_appliance_power_usage(
                    household_id, bucket_start_at, appliance_type, energy_wh, updated_at
                ) values (?, ?, ?, cast(? as numeric), ?)
                """,
                householdId,
                bucketStart(date, hour),
                applianceType,
                energyWh,
                OffsetDateTime.now());
    }

    private OffsetDateTime bucketStart(LocalDate date, int hour) {
        return date.atStartOfDay(SEOUL).plusHours(hour).toOffsetDateTime();
    }
}
