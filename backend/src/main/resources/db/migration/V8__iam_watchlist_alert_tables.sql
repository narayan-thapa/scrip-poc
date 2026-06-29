-- Stage 8 (§10.10): identity, user features, and alert configuration.

-- Application users. Email is stored lower-cased with a unique constraint (the
-- service normalizes before insert). password_hash is an Argon2id hash — never a
-- plaintext or reversibly-encrypted secret. role gates ANALYST/ADMIN endpoints.
CREATE TABLE app_user (
    id            UUID         NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,            -- USER | ANALYST | ADMIN
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

-- Refresh tokens (rotation + revocation). Only a SHA-256 hash of the opaque token
-- is stored, so a DB leak cannot reissue access tokens. The raw token lives only in
-- the client's httpOnly+Secure+SameSite cookie. Rotation revokes the prior row.
CREATE TABLE refresh_token (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- A user's named watchlist and its symbols.
CREATE TABLE watchlist (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_watchlist_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE TABLE watchlist_item (
    watchlist_id UUID        NOT NULL,
    symbol       VARCHAR(20) NOT NULL,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (watchlist_id, symbol),
    CONSTRAINT fk_item_watchlist FOREIGN KEY (watchlist_id) REFERENCES watchlist (id) ON DELETE CASCADE
);

-- User alert rules evaluated by the Stage 9 notifier on SignalsGeneratedEvent.
-- symbol is null for portfolio-wide rules; params carries type-specific thresholds.
CREATE TABLE alert_rule (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    type        VARCHAR(24)  NOT NULL,             -- SIGNAL_ACTION | SCORE_THRESHOLD | WATCHLIST_SIGNAL
    symbol      VARCHAR(20),
    params      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_alert_rule_user ON alert_rule (user_id);
CREATE INDEX idx_alert_rule_enabled ON alert_rule (enabled) WHERE enabled;
