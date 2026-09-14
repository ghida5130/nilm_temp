CREATE TABLE staff_profile (
    id                 BIGSERIAL PRIMARY KEY,
    auth_sub           VARCHAR(255) NOT NULL UNIQUE,
    display_name       VARCHAR(100) NOT NULL,
    organization_name  VARCHAR(100) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_staff_profile_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE care_subject (
    id              BIGSERIAL PRIMARY KEY,
    risk_policy_id  BIGINT,
    household_id    VARCHAR(50) NOT NULL,
    subject_number  VARCHAR(50) NOT NULL UNIQUE,
    birth_date      DATE NOT NULL,
    name            BYTEA NOT NULL,
    phone           BYTEA NOT NULL,
    address         BYTEA NOT NULL,
    region_code     VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    registered_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at    TIMESTAMP WITH TIME ZONE,
    ended_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_care_subject_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'PAUSED', 'ENDED', 'DECEASED'))
);

CREATE TABLE staff_subject_assignment (
    id                    BIGSERIAL PRIMARY KEY,
    staff_id              BIGINT NOT NULL REFERENCES staff_profile (id),
    subject_id            BIGINT NOT NULL REFERENCES care_subject (id),
    assigned_by_staff_id  BIGINT NOT NULL REFERENCES staff_profile (id),
    memo                  TEXT,
    assigned_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    unassigned_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_staff_subject_assignment_staff
    ON staff_subject_assignment (staff_id, unassigned_at);
CREATE INDEX ix_staff_subject_assignment_subject
    ON staff_subject_assignment (subject_id, unassigned_at);

CREATE TABLE risk_policy (
    id                    BIGSERIAL PRIMARY KEY,
    policy_code           VARCHAR(50) NOT NULL,
    policy_name           VARCHAR(100) NOT NULL,
    version               INTEGER NOT NULL,
    algorithm_type        VARCHAR(50) NOT NULL,
    warning_threshold     SMALLINT,
    danger_threshold      SMALLINT,
    min_duration_seconds  INTEGER NOT NULL,
    parameters            JSONB NOT NULL,
    change_reason         TEXT NOT NULL,
    created_by_staff_id   BIGINT NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_risk_policy_version UNIQUE (policy_code, version),
    CONSTRAINT ck_risk_policy_version CHECK (version > 0),
    CONSTRAINT ck_risk_policy_duration CHECK (min_duration_seconds >= 0),
    CONSTRAINT ck_risk_policy_thresholds CHECK (
        (warning_threshold IS NULL AND danger_threshold IS NULL)
        OR (warning_threshold BETWEEN 0 AND 100
            AND danger_threshold BETWEEN 0 AND 100
            AND warning_threshold < danger_threshold)
    )
);

CREATE TABLE power_usage_hourly (
    household_id         VARCHAR(50) NOT NULL,
    hour_start           TIMESTAMP WITH TIME ZONE NOT NULL,
    energy_wh            NUMERIC(14, 3) NOT NULL,
    avg_active_power_w   NUMERIC(12, 3) NOT NULL,
    max_active_power_w   NUMERIC(12, 3) NOT NULL,
    sample_count         INTEGER NOT NULL,
    coverage_ratio       NUMERIC(5, 4) NOT NULL,
    aggregation_version  VARCHAR(30) NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (household_id, hour_start),
    CONSTRAINT ck_power_usage_hourly_values CHECK (
        energy_wh >= 0
        AND avg_active_power_w >= 0
        AND max_active_power_w >= avg_active_power_w
        AND sample_count >= 0
        AND coverage_ratio BETWEEN 0 AND 1
    )
);

CREATE TABLE notification_preference (
    id                BIGSERIAL PRIMARY KEY,
    staff_id          BIGINT NOT NULL REFERENCES staff_profile (id),
    notification_type VARCHAR(50) NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    enabled           BOOLEAN NOT NULL,
    minimum_severity  VARCHAR(20) NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_notification_preference
        UNIQUE (staff_id, notification_type, channel),
    CONSTRAINT ck_notification_preference_channel
        CHECK (channel IN ('IN_APP', 'WEB_PUSH', 'EMAIL', 'SMS')),
    CONSTRAINT ck_notification_preference_severity
        CHECK (minimum_severity IN ('WARNING', 'DANGER'))
);

CREATE TABLE ai_report (
    id                  UUID PRIMARY KEY,
    staff_id            BIGINT NOT NULL REFERENCES staff_profile (id),
    period_start        TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end          TIMESTAMP WITH TIME ZONE NOT NULL,
    title               VARCHAR(200) NOT NULL,
    content             TEXT NOT NULL,
    summary_data        JSONB NOT NULL,
    evidence_event_ids  JSONB NOT NULL,
    model_version       VARCHAR(100) NOT NULL,
    generated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_ai_report_period CHECK (period_start < period_end)
);

ALTER TABLE analysis_event ADD COLUMN subject_id BIGINT;
ALTER TABLE analysis_event ADD COLUMN appliance_type VARCHAR(50);
ALTER TABLE analysis_event ADD COLUMN risk_level VARCHAR(20);
ALTER TABLE analysis_event ADD COLUMN risk_policy_id BIGINT;
ALTER TABLE analysis_event ADD COLUMN policy_version INTEGER;
ALTER TABLE analysis_event ADD COLUMN model_version VARCHAR(50);
ALTER TABLE analysis_event ALTER COLUMN event_type SET DATA TYPE VARCHAR(50);
ALTER TABLE analysis_event ADD CONSTRAINT ck_analysis_event_policy_reference CHECK (
    (risk_policy_id IS NULL AND policy_version IS NULL)
    OR (risk_policy_id IS NOT NULL AND policy_version > 0)
);
ALTER TABLE analysis_event ADD CONSTRAINT ck_analysis_event_risk_level CHECK (
    risk_level IS NULL OR risk_level IN ('NORMAL', 'WARNING', 'DANGER')
);

ALTER TABLE incident ADD COLUMN subject_id BIGINT;
ALTER TABLE incident ADD COLUMN severity VARCHAR(20);
ALTER TABLE incident ADD COLUMN assigned_staff_id BIGINT;
ALTER TABLE incident ADD COLUMN acknowledged_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incident ADD COLUMN cleared_event_id UUID;
ALTER TABLE incident ADD COLUMN resolved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incident ADD COLUMN resolution_code VARCHAR(30);
ALTER TABLE incident ALTER COLUMN incident_type SET DATA TYPE VARCHAR(50);
ALTER TABLE incident DROP CONSTRAINT ck_incident_status;
ALTER TABLE incident ADD CONSTRAINT ck_incident_status CHECK (
    status IN ('OPEN', 'CONFIRMED', 'FALSE_POSITIVE', 'ACKNOWLEDGED', 'RESOLVED')
);
ALTER TABLE incident ADD CONSTRAINT ck_incident_severity CHECK (
    severity IS NULL OR severity IN ('WARNING', 'DANGER')
);

ALTER TABLE incident_action ADD COLUMN actor_staff_id BIGINT;
ALTER TABLE incident_action ADD COLUMN note TEXT;
ALTER TABLE incident_action ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incident_action ALTER COLUMN actor_type SET DATA TYPE VARCHAR(20);
ALTER TABLE incident_action ALTER COLUMN actor_id SET DATA TYPE VARCHAR(255);
UPDATE incident_action SET created_at = occurred_at WHERE created_at IS NULL;
ALTER TABLE incident_action ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE incident_action DROP CONSTRAINT ck_incident_action_actor_type;
ALTER TABLE incident_action ADD CONSTRAINT ck_incident_action_actor_type CHECK (
    actor_type IN ('SYSTEM', 'USER', 'STAFF')
);

ALTER TABLE household_access ADD COLUMN access_role VARCHAR(30);
ALTER TABLE household_access ALTER COLUMN user_id SET DATA TYPE VARCHAR(255);
ALTER TABLE push_subscription ALTER COLUMN user_id SET DATA TYPE VARCHAR(255);
ALTER TABLE notification_delivery ALTER COLUMN recipient_user_id SET DATA TYPE VARCHAR(255);
ALTER TABLE notification_delivery
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'WEB_PUSH';
ALTER TABLE notification_delivery ADD CONSTRAINT ck_notification_delivery_channel CHECK (
    channel IN ('IN_APP', 'WEB_PUSH', 'EMAIL', 'SMS')
);
ALTER TABLE ui_outbox ALTER COLUMN event_name SET DATA TYPE VARCHAR(50);
