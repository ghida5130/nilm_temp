package com.nilm.monitoring;

import com.nilm.monitoring.config.LocalSubjectInitializer;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class LocalSubjectInitializerTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:seed_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void createsSubjectUsingConfiguredHouseholdAndSubscriptionIdentity() {
        new LocalSubjectInitializer(jdbc, "REAL-HOUSE", "test-subject-3").afterSingletonsInstantiated();
        var row = jdbc.queryForMap("select * from subjects");
        assertThat(row.get("household_id")).isEqualTo("REAL-HOUSE");
        assertThat(row.get("auth_sub")).isEqualTo("test-subject-3");
        assertThat(row.get("monitoring_enabled")).isEqualTo(true);
    }

    @Test
    void restartDoesNotDuplicateOrOverwriteExistingState() {
        var initializer = new LocalSubjectInitializer(jdbc, "HOUSE001", "test-subject-3");
        initializer.afterSingletonsInstantiated();
        jdbc.update("update subjects set name='수정한 이름', monitoring_enabled=false");
        initializer.afterSingletonsInstantiated();
        assertThat(jdbc.queryForObject("select count(*) from subjects", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from subjects", String.class)).isEqualTo("수정한 이름");
        assertThat(jdbc.queryForObject("select monitoring_enabled from subjects", Boolean.class)).isFalse();
    }

    @Test
    void changedConfigurationDoesNotReassignExistingUserOrDuplicateHousehold() {
        new LocalSubjectInitializer(jdbc, "HOUSE001", "test-subject-3").afterSingletonsInstantiated();
        new LocalSubjectInitializer(jdbc, "HOUSE002", "test-subject-3").afterSingletonsInstantiated();
        new LocalSubjectInitializer(jdbc, "HOUSE001", "another-user").afterSingletonsInstantiated();
        assertThat(jdbc.queryForObject("select count(*) from subjects", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select household_id from subjects", String.class)).isEqualTo("HOUSE001");
        assertThat(jdbc.queryForObject("select auth_sub from subjects", String.class)).isEqualTo("test-subject-3");
    }
}

