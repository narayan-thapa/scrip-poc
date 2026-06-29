-- V1 baseline. Per-module schema arrives in later stages using the naming
-- convention  V<n>__<module>_<description>.sql  (e.g. V2__reference_instruments.sql,
-- V3__ingestion_floorsheet_trade.sql). Keep this baseline DB-agnostic so it runs
-- on both the TimescaleDB runtime image and the plain Postgres test container.
--
-- Spring Batch owns its own metadata tables (BATCH_*), created at runtime via
-- spring.batch.jdbc.initialize-schema; they are intentionally not defined here.

CREATE TABLE IF NOT EXISTS schema_baseline (
    component   VARCHAR(64)  PRIMARY KEY,
    applied_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO schema_baseline (component) VALUES ('platform')
    ON CONFLICT (component) DO NOTHING;
