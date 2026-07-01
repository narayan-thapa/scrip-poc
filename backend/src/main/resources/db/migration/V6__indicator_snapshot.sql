-- Phase 4: canonical per-(symbol, date) indicator snapshot. A few promoted columns for fast
-- filtering (used by screeners/signals later) + the full set as JSONB.
CREATE TABLE indicator_snapshot (
    symbol     VARCHAR(20)   NOT NULL,
    trade_date DATE          NOT NULL,
    values     JSONB         NOT NULL,
    rsi14      NUMERIC(18,4),
    ema9       NUMERIC(18,4),
    ema21      NUMERIC(18,4),
    atr14      NUMERIC(18,4),
    PRIMARY KEY (symbol, trade_date)
);
CREATE INDEX idx_indicator_snapshot_date ON indicator_snapshot (trade_date);
