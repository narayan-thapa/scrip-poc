-- Phase 3: derived market data. These daily aggregates are small (≈ hundreds of symbols × 1 row/day),
-- so no partitioning — kept hot.

CREATE TABLE daily_candle (
    symbol       VARCHAR(20)   NOT NULL,
    trade_date   DATE          NOT NULL,
    open         NUMERIC(18,4) NOT NULL,
    high         NUMERIC(18,4) NOT NULL,
    low          NUMERIC(18,4) NOT NULL,
    close        NUMERIC(18,4) NOT NULL,
    volume       BIGINT        NOT NULL,
    turnover     NUMERIC(20,4) NOT NULL,
    trades_count INT           NOT NULL,
    vwap         NUMERIC(18,4) NOT NULL,
    prev_close   NUMERIC(18,4),
    change_pct   NUMERIC(10,4),
    PRIMARY KEY (symbol, trade_date)
);
CREATE INDEX idx_daily_candle_date ON daily_candle (trade_date);

CREATE TABLE market_aggregate_daily (
    trade_date         DATE          PRIMARY KEY,
    total_volume       BIGINT        NOT NULL,
    total_turnover     NUMERIC(22,4) NOT NULL,
    total_trades       BIGINT        NOT NULL,
    advances           INT           NOT NULL,
    declines           INT           NOT NULL,
    unchanged          INT           NOT NULL,
    index_proxy_open   NUMERIC(22,4),   -- cap-weighted proxy (approximate; needs listed_shares)
    index_proxy_high   NUMERIC(22,4),
    index_proxy_low    NUMERIC(22,4),
    index_proxy_close  NUMERIC(22,4),
    official_index_close NUMERIC(18,4)  -- nullable: ingested from a separate feed
);

CREATE TABLE volume_profile (
    symbol      VARCHAR(20)   NOT NULL,
    window_from DATE          NOT NULL,
    window_to   DATE          NOT NULL,
    poc         NUMERIC(18,4) NOT NULL,
    vah         NUMERIC(18,4) NOT NULL,
    val         NUMERIC(18,4) NOT NULL,
    bins        JSONB         NOT NULL,
    PRIMARY KEY (symbol, window_from, window_to)
);

CREATE TABLE broker_flow_daily (
    symbol      VARCHAR(20)   NOT NULL,
    trade_date  DATE          NOT NULL,
    broker_id   INT           NOT NULL,
    buy_qty     BIGINT        NOT NULL,
    sell_qty    BIGINT        NOT NULL,
    net_qty     BIGINT        NOT NULL,
    buy_amount  NUMERIC(20,4) NOT NULL,
    sell_amount NUMERIC(20,4) NOT NULL,
    PRIMARY KEY (symbol, trade_date, broker_id)
);
CREATE INDEX idx_broker_flow_symbol_date ON broker_flow_daily (symbol, trade_date);
