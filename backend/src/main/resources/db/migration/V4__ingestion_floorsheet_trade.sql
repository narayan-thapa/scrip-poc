-- Phase 2: raw floorsheet trades + ingestion bookkeeping.

-- High-volume table: monthly RANGE partitions on trade_date. The PK includes the partition key
-- (required by Postgres) and contract_id is globally unique per trade, giving idempotent upserts.
CREATE TABLE floorsheet_trade (
    contract_id    VARCHAR(32)   NOT NULL,
    symbol         VARCHAR(20)   NOT NULL,
    buyer_broker   INT           NOT NULL,
    seller_broker  INT           NOT NULL,
    quantity       BIGINT        NOT NULL,
    price          NUMERIC(18,4) NOT NULL,
    amount         NUMERIC(20,4) NOT NULL,
    trade_time     TIMESTAMP     NOT NULL,
    trade_date     DATE          NOT NULL,
    source_file_id UUID,
    ingested_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (contract_id, trade_date)
) PARTITION BY RANGE (trade_date);

-- DEFAULT partition so any date ingests without pre-creating monthly partitions; ops adds
-- explicit monthly partitions later. Indexes on the parent propagate to all partitions.
CREATE TABLE floorsheet_trade_default PARTITION OF floorsheet_trade DEFAULT;
CREATE INDEX idx_floorsheet_symbol_date ON floorsheet_trade (symbol, trade_date);
CREATE INDEX brin_floorsheet_trade_time ON floorsheet_trade USING BRIN (trade_time);

CREATE TABLE ingestion_batch (
    id           UUID         PRIMARY KEY,
    file_count   INT          NOT NULL,
    date_from    DATE,
    date_to      DATE,
    status       VARCHAR(16)  NOT NULL,           -- QUEUED | RUNNING | COMPLETED | FAILED | PARTIAL
    submitted_by VARCHAR(255),
    submitted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ
);

-- One row per file/date. Multiple rows per date are allowed (reprocessing history); row-level
-- idempotency is enforced by the floorsheet_trade.contract_id upsert, not here.
CREATE TABLE ingestion_job (
    id              UUID         PRIMARY KEY,
    batch_id        UUID         REFERENCES ingestion_batch (id) ON DELETE CASCADE,
    trade_date      DATE         NOT NULL,
    source_filename VARCHAR(255) NOT NULL,
    file_hash       VARCHAR(64)  NOT NULL,
    archive_key     VARCHAR(255),
    rows_read       INT          NOT NULL DEFAULT 0,
    rows_accepted   INT          NOT NULL DEFAULT 0,
    rows_rejected   INT          NOT NULL DEFAULT 0,
    rows_duplicate  INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL,         -- QUEUED | RUNNING | COMPLETED | FAILED
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
CREATE INDEX idx_ingestion_job_date ON ingestion_job (trade_date, started_at);
CREATE INDEX idx_ingestion_job_batch ON ingestion_job (batch_id);

CREATE TABLE ingestion_rejection (
    id          BIGSERIAL    PRIMARY KEY,
    job_id      UUID         NOT NULL REFERENCES ingestion_job (id) ON DELETE CASCADE,
    raw_line    TEXT,
    reason_code VARCHAR(32)  NOT NULL,
    detail      TEXT
);
CREATE INDEX idx_ingestion_rejection_job ON ingestion_rejection (job_id);
