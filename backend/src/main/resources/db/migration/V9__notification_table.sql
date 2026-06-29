-- Stage 9 (§10.9): the user notification feed + dispatch outbox.
-- One row per (user, signal) — the unique constraint is the dedup guard, so a rerun
-- of a day's signals never double-notifies. `sent` is a lightweight outbox flag: the
-- dispatcher pushes unsent rows over WebSocket/SSE and flips it, so a delivery crash
-- leaves the row to be retried rather than lost. `read` is the user's UI state.
CREATE TABLE notification (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    signal_id   UUID,
    title       VARCHAR(160) NOT NULL,
    body        TEXT         NOT NULL,
    read        BOOLEAN      NOT NULL DEFAULT FALSE,
    sent        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_user_signal UNIQUE (user_id, signal_id)
);
CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unsent ON notification (sent) WHERE NOT sent;
