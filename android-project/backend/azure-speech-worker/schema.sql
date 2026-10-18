CREATE TABLE IF NOT EXISTS speech_usage (
  bucket TEXT PRIMARY KEY,
  used_chars INTEGER NOT NULL DEFAULT 0 CHECK(used_chars >= 0)
);
