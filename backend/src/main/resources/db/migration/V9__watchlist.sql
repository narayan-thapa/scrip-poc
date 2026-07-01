-- Phase 8: user watchlists.

CREATE TABLE watchlist (
    id      UUID         PRIMARY KEY,
    user_id UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name    VARCHAR(120) NOT NULL
);
CREATE INDEX idx_watchlist_user ON watchlist (user_id);

CREATE TABLE watchlist_item (
    watchlist_id UUID        NOT NULL REFERENCES watchlist (id) ON DELETE CASCADE,
    symbol       VARCHAR(20) NOT NULL,
    PRIMARY KEY (watchlist_id, symbol)
);
