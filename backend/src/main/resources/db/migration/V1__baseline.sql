-- V1 baseline (Phase 0).
--
-- Establishes the migration history and the naming convention. Feature tables are added by their
-- owning module in later phases, e.g.:
--   V2__reference_instrument_broker_calendar.sql   (Phase 1)
--   V3__ingestion_floorsheet_trade.sql             (Phase 2, monthly RANGE partitions)
--   V4__marketdata_candle_volprofile_brokerflow.sql(Phase 3)
-- High-volume tables (floorsheet_trade, intraday_candle) adopt monthly partitioning /
-- TimescaleDB at that point; nothing here pre-creates them.

CREATE TABLE schema_bootstrap (
    id          SMALLINT     PRIMARY KEY,
    description TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO schema_bootstrap (id, description)
VALUES (1, 'Phase 0 platform baseline');
