-- Backtest module (§10.7): historical replay of the confluence model with the NEPSE
-- cost model. A run records its parameters; the result holds summary metrics + the
-- equity/drawdown curve; trades are the per-position ledger with entry/exit reasons.
-- Runs are versioned (one immutable row per execution) so results can be compared.
CREATE TABLE backtest_run (
    id                UUID          NOT NULL,
    strategy_label    VARCHAR(128)  NOT NULL,
    symbols           JSONB         NOT NULL,          -- array of symbols covered
    date_from         DATE          NOT NULL,
    date_to           DATE          NOT NULL,
    starting_capital  NUMERIC(20,2) NOT NULL,
    cost_model        JSONB         NOT NULL,          -- CostModelSpec used for this run
    status            VARCHAR(12)   NOT NULL,          -- PENDING | RUNNING | COMPLETED | FAILED
    error_message     TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX idx_backtest_run_created ON backtest_run (created_at DESC);

-- One result row per run (1:1). Metrics + the equity/drawdown curve are JSONB; the
-- curve is small (one point per trading day in range) so it is kept inline rather
-- than offloaded to object storage.
CREATE TABLE backtest_result (
    run_id        UUID          NOT NULL,
    metrics       JSONB         NOT NULL,
    equity_curve  JSONB         NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id),
    CONSTRAINT fk_result_run FOREIGN KEY (run_id) REFERENCES backtest_run (id) ON DELETE CASCADE
);

-- Per-position trade ledger. exit_* are nullable for a position still open at the
-- end of the backtest window. costs/pnl are the NEPSE-cost-adjusted figures.
CREATE TABLE backtest_trade (
    id            UUID          NOT NULL,
    run_id        UUID          NOT NULL,
    symbol        VARCHAR(20)   NOT NULL,
    entry_date    DATE          NOT NULL,
    entry_price   NUMERIC(18,4) NOT NULL,
    exit_date     DATE,
    exit_price    NUMERIC(18,4),
    quantity      BIGINT        NOT NULL,
    costs         NUMERIC(20,2) NOT NULL,
    pnl           NUMERIC(20,2) NOT NULL,
    return_pct    NUMERIC(12,4) NOT NULL,
    entry_reason  TEXT          NOT NULL,
    exit_reason   TEXT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_trade_run FOREIGN KEY (run_id) REFERENCES backtest_run (id) ON DELETE CASCADE
);
CREATE INDEX idx_backtest_trade_run ON backtest_trade (run_id, entry_date);
