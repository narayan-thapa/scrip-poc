-- Reference module: master data for instruments, brokers, and the trading calendar.

CREATE TABLE instrument (
    symbol        VARCHAR(20)  PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    sector        VARCHAR(64),
    listed_shares BIGINT,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    price_band    VARCHAR(32),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_instrument_sector ON instrument (sector);
CREATE INDEX idx_instrument_status ON instrument (status);

CREATE TABLE broker (
    broker_id INTEGER     PRIMARY KEY,
    name      VARCHAR(128) NOT NULL,
    status    VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
);

-- One row per calendar day in the seeded window; is_open folds in weekends + holidays
-- so every look-back counts trading days, not calendar days.
CREATE TABLE trading_day (
    trade_date DATE         PRIMARY KEY,
    is_open    BOOLEAN      NOT NULL,
    note       VARCHAR(128)
);
CREATE INDEX idx_trading_day_open ON trading_day (trade_date) WHERE is_open;
