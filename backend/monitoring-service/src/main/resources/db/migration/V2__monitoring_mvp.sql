CREATE TABLE analysis_event (
    id               UUID PRIMARY KEY,
    household_id     VARCHAR(50) NOT NULL,
    event_type       VARCHAR(40) NOT NULL DEFAULT 'ROUTINE_MISSED',
    score            SMALLINT NOT NULL CHECK (score BETWEEN 0 AND 100),
    event_date       DATE NOT NULL,
    expected_until   TIME NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    reason           JSONB NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_analysis_event_logical
        UNIQUE (household_id, event_type, event_date, expected_until)
);

CREATE INDEX ix_analysis_event_household ON analysis_event (household_id, occurred_at DESC);

CREATE TABLE incident (
    id                UUID PRIMARY KEY,
    analysis_event_id UUID NOT NULL UNIQUE REFERENCES analysis_event (id),
    household_id      VARCHAR(50) NOT NULL,
    incident_type     VARCHAR(40) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    opened_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at         TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_incident_status CHECK (status IN ('OPEN', 'CONFIRMED', 'FALSE_POSITIVE'))
);

CREATE INDEX ix_incident_household_status ON incident (household_id, status, opened_at DESC);

CREATE TABLE household_access (
    id               BIGSERIAL PRIMARY KEY,
    household_id     VARCHAR(50) NOT NULL,
    user_id          VARCHAR(100) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_household_access UNIQUE (household_id, user_id)
);

CREATE INDEX ix_household_access_user ON household_access (user_id);

CREATE TABLE push_subscription (
    id               BIGSERIAL PRIMARY KEY,
    user_id          VARCHAR(100) NOT NULL,
    endpoint         TEXT NOT NULL UNIQUE,
    p256dh           TEXT NOT NULL,
    auth             TEXT NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE,
    revoked_at       TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_push_subscription_user_active ON push_subscription (user_id, revoked_at);

CREATE TABLE notification_delivery (
    id                    UUID PRIMARY KEY,
    incident_id           UUID NOT NULL REFERENCES incident (id),
    recipient_user_id     VARCHAR(100) NOT NULL,
    delivery_status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    payload               JSONB NOT NULL,
    sent_at               TIMESTAMP WITH TIME ZONE,
    response_deadline_at  TIMESTAMP WITH TIME ZONE,
    answer                VARCHAR(10),
    response_source       VARCHAR(10),
    responded_at          TIMESTAMP WITH TIME ZONE,
    failure_reason        TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_notification_delivery_recipient UNIQUE (incident_id, recipient_user_id),
    CONSTRAINT ck_notification_delivery_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_delivery_answer CHECK (answer IS NULL OR answer IN ('yes', 'no')),
    CONSTRAINT ck_notification_delivery_source
        CHECK (response_source IS NULL OR response_source IN ('user', 'timeout')),
    CONSTRAINT ck_notification_delivery_response_complete CHECK (
        (answer IS NULL AND response_source IS NULL AND responded_at IS NULL)
        OR (answer IS NOT NULL AND response_source IS NOT NULL AND responded_at IS NOT NULL)
    )
);

CREATE INDEX ix_notification_delivery_pending ON notification_delivery (delivery_status, id);
CREATE INDEX ix_notification_delivery_timeout
    ON notification_delivery (delivery_status, response_deadline_at);
CREATE INDEX ix_notification_delivery_incident ON notification_delivery (incident_id);

CREATE TABLE incident_action (
    id                        BIGSERIAL PRIMARY KEY,
    incident_id               UUID NOT NULL REFERENCES incident (id),
    notification_delivery_id  UUID REFERENCES notification_delivery (id),
    action_type               VARCHAR(30) NOT NULL,
    actor_type                VARCHAR(10) NOT NULL,
    actor_id                  VARCHAR(100),
    previous_status           VARCHAR(20),
    next_status               VARCHAR(20) NOT NULL,
    occurred_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_incident_action_actor_type CHECK (actor_type IN ('SYSTEM', 'USER'))
);

CREATE INDEX ix_incident_action_history ON incident_action (incident_id, id);

CREATE TABLE ui_outbox (
    id               BIGSERIAL PRIMARY KEY,
    household_id     VARCHAR(50) NOT NULL,
    event_name       VARCHAR(40) NOT NULL,
    payload          JSONB NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_ui_outbox_unpublished ON ui_outbox (published_at, id);
CREATE INDEX ix_ui_outbox_replay ON ui_outbox (household_id, id);
