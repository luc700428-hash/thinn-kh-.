CREATE TABLE IF NOT EXISTS deliveries (
  event_id TEXT PRIMARY KEY NOT NULL,
  revision INTEGER NOT NULL,
  claimed_at INTEGER NOT NULL,
  sent INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_deliveries_claimed_at ON deliveries(claimed_at);
