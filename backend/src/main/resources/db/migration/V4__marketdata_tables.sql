-- Market data module: price/volume structure derived from floorsheet trades.
-- Every table here is a deterministic function of floorsheet_trade for a (symbol,
-- trade_date); recomputation replaces the prior rows so aggregation is idempotent.

-- One OHLCV row per (symbol, trade_date). Small (≈ one row per listed scrip per
-- day), so it is not partitioned. previous_close / change_percent are nullable:
-- the first observed day for a symbol has no prior close.
CREATE TABLE daily_candle (
    symbol          VARCHAR(20)   NOT NULL,
    trade_date      DATE          NOT NULL,
    open_price      NUMERIC(18,4) NOT NULL,
    high_price      NUMERIC(18,4) NOT NULL,
    low_price       NUMERIC(18,4) NOT NULL,
    close_price     NUMERIC(18,4) NOT NULL,
    volume          BIGINT        NOT NULL,
    turnover        NUMERIC(20,4) NOT NULL,
    vwap            NUMERIC(18,4) NOT NULL,
    previous_close  NUMERIC(18,4),
    change_percent  NUMERIC(9,4),
    trade_count     INTEGER       NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (symbol, trade_date)
);
CREATE INDEX idx_daily_candle_date ON daily_candle (trade_date);

-- Intraday OHLCV buckets. Partitioned by month on trade_date like floorsheet_trade
-- (potentially high row count); the PK must include the partition key. bucket_start
-- is the inclusive lower edge of the time bucket.
CREATE TABLE intraday_candle (
    symbol          VARCHAR(20)   NOT NULL,
    trade_date      DATE          NOT NULL,
    bucket_start    TIMESTAMP     NOT NULL,
    interval_minutes INTEGER      NOT NULL,
    open_price      NUMERIC(18,4) NOT NULL,
    high_price      NUMERIC(18,4) NOT NULL,
    low_price       NUMERIC(18,4) NOT NULL,
    close_price     NUMERIC(18,4) NOT NULL,
    volume          BIGINT        NOT NULL,
    turnover        NUMERIC(20,4) NOT NULL,
    trade_count     INTEGER       NOT NULL,
    PRIMARY KEY (symbol, trade_date, bucket_start)
) PARTITION BY RANGE (trade_date);

CREATE TABLE intraday_candle_default PARTITION OF intraday_candle DEFAULT;
CREATE INDEX idx_intraday_candle_symbol_date ON intraday_candle (symbol, trade_date);

-- True volume-at-price profile (§6.2). Summary columns are promoted for cheap
-- querying; the full bin array is kept as JSONB for charting overlays.
CREATE TABLE volume_profile (
    symbol             VARCHAR(20)   NOT NULL,
    trade_date         DATE          NOT NULL,
    bin_count          INTEGER       NOT NULL,
    bin_width          NUMERIC(18,6) NOT NULL,
    price_min          NUMERIC(18,4) NOT NULL,
    price_max          NUMERIC(18,4) NOT NULL,
    poc_price          NUMERIC(18,4) NOT NULL,
    value_area_high    NUMERIC(18,4) NOT NULL,
    value_area_low     NUMERIC(18,4) NOT NULL,
    total_volume       BIGINT        NOT NULL,
    value_area_volume  BIGINT        NOT NULL,
    bins               JSONB         NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (symbol, trade_date)
);

-- Per-broker net flow (§6.3): one row per (symbol, trade_date, broker). Top-N
-- share / Herfindahl concentration are derived on read from these rows.
CREATE TABLE broker_flow_daily (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol      VARCHAR(20)   NOT NULL,
    trade_date  DATE          NOT NULL,
    broker_id   INTEGER       NOT NULL,
    buy_qty     BIGINT        NOT NULL,
    sell_qty    BIGINT        NOT NULL,
    net_qty     BIGINT        NOT NULL,
    buy_amount  NUMERIC(20,4) NOT NULL,
    sell_amount NUMERIC(20,4) NOT NULL,
    net_amount  NUMERIC(20,4) NOT NULL,
    CONSTRAINT uq_broker_flow_daily UNIQUE (symbol, trade_date, broker_id)
);
CREATE INDEX idx_broker_flow_symbol_date ON broker_flow_daily (symbol, trade_date);
