CREATE TABLE IF NOT EXISTS worlds (
  id             TEXT PRIMARY KEY,
  created_at     INTEGER NOT NULL,
  utc_offset     INTEGER NOT NULL DEFAULT 0,
  me_name        TEXT NOT NULL,
  me_handle      TEXT NOT NULL,
  me_avatar      INTEGER NOT NULL DEFAULT 0,
  next_tick_at   INTEGER NOT NULL DEFAULT 0,
  quiet_until    INTEGER NOT NULL DEFAULT 0,
  calls_today    INTEGER NOT NULL DEFAULT 0,
  calls_day      TEXT NOT NULL DEFAULT '',
  daily_budget   INTEGER NOT NULL DEFAULT 200,
  last_contact_at INTEGER NOT NULL DEFAULT 0,
  endpoint       TEXT,
  api_key        TEXT,
  model          TEXT
);

CREATE TABLE IF NOT EXISTS world_keys (
  world_id TEXT PRIMARY KEY,
  key_hash TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS personas (
  world_id       TEXT NOT NULL,
  id             TEXT NOT NULL,
  name           TEXT NOT NULL,
  handle         TEXT NOT NULL,
  bio            TEXT NOT NULL DEFAULT '',
  occupation     TEXT NOT NULL DEFAULT '',
  location       TEXT NOT NULL DEFAULT '',
  voice          TEXT NOT NULL DEFAULT '',
  gradient       TEXT NOT NULL DEFAULT '[]',
  joined_year    TEXT NOT NULL DEFAULT '',
  follower_boost INTEGER NOT NULL DEFAULT 0,
  model          TEXT,
  custom         INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS relations (
  world_id TEXT NOT NULL,
  a        TEXT NOT NULL,
  b        TEXT NOT NULL,
  label    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS posts (
  world_id    TEXT NOT NULL,
  id          TEXT NOT NULL,
  author_id   TEXT NOT NULL,
  text        TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  likes       INTEGER NOT NULL DEFAULT 0,
  replies     INTEGER NOT NULL DEFAULT 0,
  liked_by_me INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS replies (
  world_id   TEXT NOT NULL,
  id         TEXT NOT NULL,
  post_id    TEXT NOT NULL,
  author_id  TEXT NOT NULL,
  text       TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS stories (
  world_id   TEXT NOT NULL,
  id         TEXT NOT NULL,
  persona_id TEXT NOT NULL,
  text       TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS dms (
  world_id   TEXT NOT NULL,
  id         TEXT NOT NULL,
  persona_id TEXT NOT NULL,
  from_me    INTEGER NOT NULL DEFAULT 0,
  text       TEXT NOT NULL,
  at         INTEGER NOT NULL,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS groups (
  world_id   TEXT NOT NULL,
  id         TEXT NOT NULL,
  name       TEXT NOT NULL,
  topic      TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS group_members (
  world_id   TEXT NOT NULL,
  group_id   TEXT NOT NULL,
  persona_id TEXT NOT NULL,
  at         INTEGER NOT NULL,
  PRIMARY KEY (world_id, group_id, persona_id)
);

CREATE TABLE IF NOT EXISTS group_msgs (
  world_id  TEXT NOT NULL,
  id        TEXT NOT NULL,
  group_id  TEXT NOT NULL,
  author_id TEXT NOT NULL,
  text      TEXT NOT NULL,
  at        INTEGER NOT NULL,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS comment_asks (
  world_id   TEXT NOT NULL,
  id         TEXT NOT NULL,
  post_id    TEXT NOT NULL,
  persona_id TEXT NOT NULL,
  at         INTEGER NOT NULL,
  state      TEXT NOT NULL DEFAULT 'pending',
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS follows (
  world_id    TEXT NOT NULL,
  persona_id  TEXT NOT NULL,
  following   INTEGER NOT NULL DEFAULT 0,
  followed_by INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (world_id, persona_id)
);

CREATE TABLE IF NOT EXISTS activity (
  world_id   TEXT NOT NULL,
  id         TEXT NOT NULL,
  persona_id TEXT NOT NULL,
  bold_part  TEXT NOT NULL,
  rest       TEXT NOT NULL,
  kind       TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS context (
  world_id TEXT NOT NULL,
  id       TEXT NOT NULL,
  source   TEXT NOT NULL,
  text     TEXT NOT NULL,
  at       INTEGER NOT NULL,
  active   INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS orgs (
  world_id      TEXT NOT NULL,
  id            TEXT NOT NULL,
  name          TEXT NOT NULL,
  handle        TEXT NOT NULL,
  kind          TEXT NOT NULL DEFAULT 'news',
  bio           TEXT NOT NULL DEFAULT '',
  city          TEXT NOT NULL DEFAULT '',
  color         TEXT NOT NULL DEFAULT '#1D1D1F',
  monogram      TEXT NOT NULL DEFAULT '',
  feed_url      TEXT,
  active        INTEGER NOT NULL DEFAULT 1,
  created_at    INTEGER NOT NULL,
  last_fetch_at INTEGER NOT NULL DEFAULT 0,
  last_post_at  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (world_id, id)
);

CREATE TABLE IF NOT EXISTS news_seen (
  world_id TEXT NOT NULL,
  org_id   TEXT NOT NULL,
  url      TEXT NOT NULL,
  seen_at  INTEGER NOT NULL,
  PRIMARY KEY (world_id, org_id, url)
);

CREATE TABLE IF NOT EXISTS outbox (
  seq        INTEGER PRIMARY KEY AUTOINCREMENT,
  world_id   TEXT NOT NULL,
  kind       TEXT NOT NULL,
  payload    TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS jobs (
  id         TEXT PRIMARY KEY,
  world_id   TEXT NOT NULL,
  run_at     INTEGER NOT NULL,
  kind       TEXT NOT NULL,
  payload    TEXT NOT NULL DEFAULT '{}',
  done       INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS commands (
  world_id   TEXT NOT NULL,
  command_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (world_id, command_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_world ON outbox(world_id, seq);
CREATE INDEX IF NOT EXISTS idx_jobs_due ON jobs(done, run_at);
CREATE INDEX IF NOT EXISTS idx_posts_world ON posts(world_id, created_at);
CREATE INDEX IF NOT EXISTS idx_replies_post ON replies(world_id, post_id);
CREATE INDEX IF NOT EXISTS idx_dms_world ON dms(world_id, persona_id, at);
CREATE INDEX IF NOT EXISTS idx_group_msgs ON group_msgs(world_id, group_id, at);
CREATE INDEX IF NOT EXISTS idx_asks_world ON comment_asks(world_id, post_id);
