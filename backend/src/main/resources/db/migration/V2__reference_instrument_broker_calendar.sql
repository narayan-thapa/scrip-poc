-- Phase 1: reference master data — instruments, brokers, trading calendar.

CREATE TABLE instrument (
    symbol        VARCHAR(20)  PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    sector        VARCHAR(80),
    type          VARCHAR(16)  NOT NULL DEFAULT 'EQUITY',   -- EQUITY | INDEX
    listed_shares BIGINT,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | SUSPENDED | DELISTED
    price_band    NUMERIC(6,2),
    provisional   BOOLEAN      NOT NULL DEFAULT FALSE,      -- auto-discovered from floorsheet, awaiting enrichment
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_instrument_sector ON instrument (sector);
CREATE INDEX idx_instrument_status ON instrument (status);

CREATE TABLE broker (
    broker_id INT          PRIMARY KEY,                     -- NEPSE numbered broker
    name      VARCHAR(200) NOT NULL,
    status    VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE trading_day (
    trade_date DATE         PRIMARY KEY,
    is_open    BOOLEAN      NOT NULL,
    note       VARCHAR(200)                                 -- holiday name when closed
);
CREATE INDEX idx_trading_day_open ON trading_day (trade_date) WHERE is_open;
