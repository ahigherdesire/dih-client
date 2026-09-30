-- Download counts, one row per jar file.
CREATE TABLE downloads (
  file  TEXT PRIMARY KEY,
  count INTEGER NOT NULL DEFAULT 0
);

-- Who already downloaded which file today, so repeat clicks and retries count once. The key is an
-- HMAC of (day, IP, file) under the random salt below, never the IP itself; older days are pruned.
CREATE TABLE seen (
  key TEXT PRIMARY KEY,
  day TEXT NOT NULL
);
CREATE INDEX seen_day ON seen (day);

CREATE TABLE meta (
  k TEXT PRIMARY KEY,
  v TEXT NOT NULL
);
INSERT INTO meta (k, v) VALUES ('salt', lower(hex(randomblob(32))));
