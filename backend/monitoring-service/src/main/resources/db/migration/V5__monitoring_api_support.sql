ALTER TABLE staff_profile ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE staff_subject_assignment ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE care_subject ADD COLUMN name_blind_index VARCHAR(64);
ALTER TABLE ui_outbox ALTER COLUMN household_id DROP NOT NULL;
ALTER TABLE ui_outbox ADD COLUMN recipient_user_id VARCHAR(255);
ALTER TABLE ui_outbox ADD CONSTRAINT ck_ui_outbox_recipient CHECK (
    (household_id IS NOT NULL AND recipient_user_id IS NULL)
    OR (household_id IS NULL AND recipient_user_id IS NOT NULL)
);
CREATE INDEX ix_care_subject_name_blind_index ON care_subject (name_blind_index);

CREATE INDEX ix_staff_subject_assignment_active_lookup
    ON staff_subject_assignment (staff_id, subject_id, unassigned_at);

CREATE TABLE staff_region_access (
    staff_id    BIGINT NOT NULL REFERENCES staff_profile (id),
    region_code VARCHAR(20) NOT NULL,
    PRIMARY KEY (staff_id, region_code)
);

CREATE TABLE staff_setting (
    id                    BIGSERIAL PRIMARY KEY,
    staff_id              BIGINT NOT NULL UNIQUE REFERENCES staff_profile (id),
    danger_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    warning_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    daily_summary_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sound_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    default_policy_id     BIGINT REFERENCES risk_policy (id),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    revision              BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE risk_policy_change (
    id                   UUID PRIMARY KEY,
    staff_id             BIGINT NOT NULL REFERENCES staff_profile (id),
    expected_revision    BIGINT NOT NULL,
    warning_threshold    SMALLINT NOT NULL,
    danger_threshold     SMALLINT NOT NULL,
    min_duration_seconds INTEGER NOT NULL,
    change_reason        TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL,
    policy_id            BIGINT REFERENCES risk_policy (id),
    failure_code         VARCHAR(100),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_risk_policy_change_status CHECK (status IN ('PENDING', 'APPLIED', 'FAILED')),
    CONSTRAINT ck_risk_policy_change_thresholds CHECK (
        warning_threshold BETWEEN 0 AND 100
        AND danger_threshold BETWEEN 0 AND 100
        AND warning_threshold < danger_threshold
    )
);
CREATE INDEX ix_risk_policy_change_staff_status
    ON risk_policy_change (staff_id, status);
