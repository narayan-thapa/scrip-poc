-- Ingestion module: raw floorsheet trades + per-file/per-batch job tracking.

-- High-volume raw trades. Partitioned by month on trade_date; the composite PK
-- includes the partition key as Postgres requires. A DEFAULT partition catches
-- any date so ingestion never fails for lack of a month partition (ops can add
-- explicit monthly partitions later; on TimescaleDB this can become a hypertable).
CREATE TABLE floorsheet_trade (
    contract_id    VARCHAR(32)   NOT NULL,
    symbol         VARCHAR(20)   NOT NULL,
    buyer_broker   INTEGER       NOT NULL,
    seller_broker  INTEGER       NOT NULL,
    quantity       BIGINT        NOT NULL,
    price          NUMERIC(18,4) NOT NULL,
    amount         NUMERIC(20,4) NOT NULL,
    trade_time     TIMESTAMP     NOT NULL,
    trade_date     DATE          NOT NULL,
    source_file_id BIGINT,
    ingested_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (contract_id, trade_date)
) PARTITION BY RANGE (trade_date);

CREATE TABLE floorsheet_trade_default PARTITION OF floorsheet_trade DEFAULT;

CREATE INDEX idx_floorsheet_symbol_date ON floorsheet_trade (symbol, trade_date);
CREATE INDEX idx_floorsheet_trade_time ON floorsheet_trade USING brin (trade_time);

CREATE TABLE ingestion_batch (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_count   INTEGER     NOT NULL,
    date_from    DATE,
    date_to      DATE,
    status       VARCHAR(16) NOT NULL,
    submitted_by VARCHAR(128),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ
);

CREATE TABLE ingestion_job (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id        BIGINT      REFERENCES ingestion_batch (id),
    trade_date      DATE        NOT NULL,
    source_filename VARCHAR(255) NOT NULL,
    file_hash       VARCHAR(64) NOT NULL,
    rows_read       INTEGER     NOT NULL DEFAULT 0,
    rows_accepted   INTEGER     NOT NULL DEFAULT 0,
    rows_rejected   INTEGER     NOT NULL DEFAULT 0,
    rows_duplicate  INTEGER     NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
-- Multiple jobs per date are allowed (reprocessing history); row-level idempotency
-- is enforced by the floorsheet_trade (contract_id) upsert, not here.
CREATE INDEX idx_ingestion_job_date ON ingestion_job (trade_date, started_at);

CREATE TABLE ingestion_rejection (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id      BIGINT      NOT NULL REFERENCES ingestion_job (id),
    raw_line    TEXT,
    reason_code VARCHAR(48) NOT NULL,
    detail      TEXT
);
CREATE INDEX idx_ingestion_rejection_job ON ingestion_rejection (job_id);
