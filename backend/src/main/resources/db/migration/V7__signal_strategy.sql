-- Phase 5: strategy configs + generated signals.

CREATE TABLE strategy_config (
    id      VARCHAR(40)  PRIMARY KEY,           -- strategy id (S1..S8)
    name    VARCHAR(100) NOT NULL,
    type    VARCHAR(40),
    params  JSONB,
    weight  NUMERIC(6,3) NOT NULL DEFAULT 1.0,  -- confluence weight, tunable via backtests
    enabled BOOLEAN      NOT NULL DEFAULT TRUE
);

-- One signal per (symbol, trade_date); re-generation replaces it (idempotent upsert).
CREATE TABLE signal (
    id           UUID         PRIMARY KEY,
    symbol       VARCHAR(20)  NOT NULL,
    trade_date   DATE         NOT NULL,
    action       VARCHAR(8)   NOT NULL,         -- BUY | SELL | HOLD
    score        NUMERIC(8,2) NOT NULL,         -- [-100, +100]
    confidence   NUMERIC(6,4) NOT NULL,
    reasons      JSONB        NOT NULL,         -- top contributing reasons
    votes        JSONB        NOT NULL,         -- full per-strategy vote vector
    generated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (symbol, trade_date)
);
CREATE INDEX idx_signal_date_action ON signal (trade_date, action);
CREATE INDEX idx_signal_symbol_date ON signal (symbol, trade_date);
