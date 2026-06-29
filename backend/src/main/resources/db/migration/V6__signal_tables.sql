-- Signal module (§10.6): daily BUY/SELL/HOLD per scrip with a structured,
-- auditable breakdown. One signal per (symbol, trade_date); regeneration replaces
-- the prior row (idempotent), so the surrogate id is stable across recomputes via
-- the natural unique key. The full weighted vote vector and structured reasons are
-- kept as JSONB so the UI can render "why" and every signal stays auditable.
CREATE TABLE signal (
    id            UUID          NOT NULL,
    symbol        VARCHAR(20)   NOT NULL,
    trade_date    DATE          NOT NULL,
    action        VARCHAR(8)    NOT NULL,           -- BUY | SELL | HOLD
    score         NUMERIC(6,2)  NOT NULL,           -- confluence score, [-100, +100]
    bar_count     INTEGER       NOT NULL,           -- history bars the strategies saw
    votes         JSONB         NOT NULL,           -- full per-strategy vote vector + reasons
    narrative     TEXT          NOT NULL,           -- human-readable summary of the signal
    computed_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_signal_symbol_date UNIQUE (symbol, trade_date)
);
CREATE INDEX idx_signal_date ON signal (trade_date);
-- Drives the "today's BUY list" screen and Stage 6's backtest refresh.
CREATE INDEX idx_signal_date_action ON signal (trade_date, action);

-- Per-strategy confluence configuration (§6.4): weights are tunable via backtests,
-- so they live in a table, not in code. params is reserved for future per-strategy
-- knobs (e.g. EMA periods); the engine reads defaults from code when absent.
CREATE TABLE strategy_config (
    strategy_id   VARCHAR(4)    NOT NULL,           -- S1 .. S9
    label         VARCHAR(64)   NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    weight        NUMERIC(6,3)  NOT NULL,           -- relative confluence weight, >= 0
    params        JSONB,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (strategy_id),
    CONSTRAINT ck_strategy_weight_nonneg CHECK (weight >= 0)
);

-- Seed the eight contributing strategies with balanced default weights. S9
-- (confluence) is the scorer itself, not a weighted input, so it is not seeded.
INSERT INTO strategy_config (strategy_id, label, enabled, weight) VALUES
    ('S1', 'Trend following (EMA cross + ADX)', TRUE, 1.000),
    ('S2', 'Mean reversion (RSI + Bollinger)',  TRUE, 1.000),
    ('S3', 'Momentum breakout (Donchian + vol)', TRUE, 1.000),
    ('S4', 'Volume profile (value area / POC)',  TRUE, 1.000),
    ('S5', 'MACD cross',                         TRUE, 1.000),
    ('S6', 'Supertrend flip',                    TRUE, 1.000),
    ('S7', 'VWAP / money flow',                  TRUE, 1.000),
    ('S8', 'Broker accumulation / distribution', TRUE, 0.750);
