-- Charting snapshot: carry the scrip symbol on signal notifications so the feed can render a
-- server-side chart snapshot for it.
ALTER TABLE notification ADD COLUMN symbol VARCHAR(20);
