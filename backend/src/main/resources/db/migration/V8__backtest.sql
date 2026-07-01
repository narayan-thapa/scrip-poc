-- Phase 6: backtest runs, results and per-trade logs.

CREATE TABLE backtest_run (
    id               UUID         PRIMARY KEY,
    symbol           VARCHAR(20)  NOT NULL,
    date_from        DATE         NOT NULL,
    date_to          DATE         NOT NULL,
    starting_capital NUMERIC(20,2) NOT NULL,
    buy_threshold    NUMERIC(6,2) NOT NULL,
    sell_threshold   NUMERIC(6,2) NOT NULL,
    cost_model       JSONB        NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    created_by       VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_backtest_run_symbol ON backtest_run (symbol, created_at DESC);

CREATE TABLE backtest_result (
    run_id       UUID        PRIMARY KEY REFERENCES backtest_run (id) ON DELETE CASCADE,
    metrics      JSONB       NOT NULL,
    equity_curve JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE backtest_trade (
    id           BIGSERIAL     PRIMARY KEY,
    run_id       UUID          NOT NULL REFERENCES backtest_run (id) ON DELETE CASCADE,
    entry_date   DATE          NOT NULL,
    entry_price  NUMERIC(18,4) NOT NULL,
    exit_date    DATE          NOT NULL,
    exit_price   NUMERIC(18,4) NOT NULL,
    quantity     BIGINT        NOT NULL,
    costs        NUMERIC(20,4) NOT NULL,
    pnl          NUMERIC(20,4) NOT NULL,
    return_pct   NUMERIC(10,4) NOT NULL,
    entry_reason TEXT,
    exit_reason  TEXT
);
CREATE INDEX idx_backtest_trade_run ON backtest_trade (run_id);
