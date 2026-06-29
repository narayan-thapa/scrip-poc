-- Indicator module: canonical per-(symbol, trade_date) technical-indicator snapshot.
-- One row per scrip per day (small, not partitioned, like daily_candle); a few
-- close-based indicators are promoted to columns for fast filtering, the full
-- catalog is kept as JSONB. Recomputation merges on the natural key.
--
-- NB: the column is `indicator_values`, not `values` — VALUES is a reserved word
-- in SQL and would otherwise need quoting everywhere.
CREATE TABLE indicator_snapshot (
    symbol            VARCHAR(20)   NOT NULL,
    trade_date        DATE          NOT NULL,
    bar_count         INTEGER       NOT NULL,
    rsi14             NUMERIC(18,4),
    ema9              NUMERIC(18,4),
    ema21             NUMERIC(18,4),
    atr14             NUMERIC(18,4),
    indicator_values  JSONB         NOT NULL,
    computed_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (symbol, trade_date)
);
CREATE INDEX idx_indicator_snapshot_date ON indicator_snapshot (trade_date);
-- Promoted columns enable screens like "RSI < 30 today" without parsing JSONB.
CREATE INDEX idx_indicator_snapshot_rsi ON indicator_snapshot (trade_date, rsi14);
