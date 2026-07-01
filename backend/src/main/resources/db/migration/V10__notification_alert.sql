-- Phase 8: alert rules + notifications (with a lightweight outbox via the sent flag).

CREATE TABLE alert_rule (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type       VARCHAR(32)  NOT NULL,   -- WATCHLIST_BUY | SIGNAL_ACTION | RVOL_SPIKE | RVOL_DROP | PRICE_DROP
    params     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_alert_rule_user ON alert_rule (user_id);

CREATE TABLE notification (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    signal_id  UUID,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    read_flag  BOOLEAN      NOT NULL DEFAULT FALSE,
    sent       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- dedup per (user, signal); persisted in the same step that creates them (durable outbox)
    UNIQUE (user_id, signal_id)
);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unsent ON notification (sent) WHERE sent = FALSE;
