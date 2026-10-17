# Only Human — the world server

The world lives here, not on the phone. One Cloudflare Worker + one D1
database in front of the same writer the app used to run locally: the
director decides *when* and *what kind*, the model only writes the words,
and anything that fails is silence — never filler.

The design contract is in [`../docs/world-server.md`](../docs/world-server.md).
This folder is the implementation.

## Layout

```
server/
  wrangler.toml        worker config: cron heartbeat, D1 binding, model defaults
  schema.sql           the whole D1 schema (run once per database)
  src/
    index.ts           fetch router + scheduled heartbeat
    web.ts             the site: landing page + the read-only window (pure HTML, no build)
    api.ts             /v1/worlds, /v1/sync, /v1/act, /v1/export, /v1/delete
    director.ts        the clock: ticks, warm-up, replies, comment asks
    engine.ts          the one writer — retries, backoff, daily budget
    prompts.ts         persona / DM prompts (faithful port of the app's Engine.kt)
    db.ts              D1 helpers + the outbox
    util.ts            ids, recovery keys, world-local time
  scripts/smoke.mjs    end-to-end smoke test against a running worker
```

## The site

The Worker serves its own small frontend — same palette and type scale as
the app, no assets, no build step:

- `GET /` — the landing: what this server is, in as few words as possible.
- `GET /w` — the window: paste a world id + recovery key and read the feed
  from any browser, read-only. Credentials are kept in that browser only;
  auto-refreshes every 40 s; never writes.
- Deep link: `GET /w#world=<id>&key=<key>` — the fragment never leaves the
  browser and is scrubbed from the address bar on load.

World data is only ever read through the authenticated `POST /v1/sync`, so
the public pages expose nothing.

## Deploy (once)

```sh
cd server
npm install
npx wrangler login
npx wrangler d1 create only-human-worlds
# paste the printed database_id into wrangler.toml
npm run schema:remote
npx wrangler deploy
curl https://only-human-worlds.<you>.workers.dev/v1/health
```

Then open `https://only-human-worlds.<you>.workers.dev/` in a browser — the
landing page confirms the server is awake; `/w` opens the read-only window.

No secrets needed with the default keyless model
(`poolside/laguna-s-2.1:free` on the Kilo gateway). A per-world custom
endpoint/model/key can be added to the `worlds` row later for BYO users.

## Local dev

```sh
npm run schema:local     # creates the local D1 with the schema
npx wrangler dev --test-scheduled   # http://127.0.0.1:8787
node scripts/smoke.mjs http://127.0.0.1:8787 --pulse   # creates a world, posts, approves an ask
```

To fire the every-minute heartbeat by hand:

```sh
npx wrangler dev --test-scheduled
curl "http://127.0.0.1:8787/__scheduled?cron=*+*+*+*+*"
```

## The API (as agreed)

- `GET /v1/health` — liveness.
- `POST /v1/worlds` — create a world. Ships `me`, `utcOffsetMinutes`, the
  full cast (`personas`, `relations`) and the starting follow graph
  (`following`, `followedBy`). Returns `{ worldId, key }` — the key is
  shown once; only its hash is stored.
- `POST /v1/sync` `{ worldId, key, cursor? }` → `{ cursor, items }`.
  Items are opaque content: `post`, `reply`, `story`, `dm`, `ask`,
  `signal` (activity), `follow`, `persona`. No timestamps in the
  protocol — `at` is just carried through from the world's clock.
- `POST /v1/act` `{ worldId, key, action, payload, commandId }`.
  Actions: `post`, `reply`, `dm`, `follow`, `like`, `approve`, `skip`,
  `set_model`, `budget`, `note`, `create_persona`.
  `commandId` makes retries safe (dedup, 1 day).
- `POST /v1/export` — full JSON dump of the world.
- `POST /v1/delete` `{ …, confirm: "delete" }` — hard delete.

## How the world runs

- **Heartbeat**: a cron trigger every minute runs due jobs: world ticks
  (scheduled 10–40 min apart while awake), warm-up writes, DM replies,
  comment asks and comment writes. Interactive things land roughly a
  minute or two after you do them — slow enough to feel human.
- **Quiet hours**: the world sleeps 01:00–06:30 *your local time* (the
  offset you shipped at creation), waking ~06:45.
- **Budget**: `daily_budget` per world (default 200, adjustable 0–1000
  via `budget`). Ambient life stops at 80% so anything you drive always
  has calls left. When the budget is gone, personas are simply quiet.
- **Silence rule**: a failed model call writes nothing, ever. Failed
  comment approvals return the ask to `pending` so you can ask again.
- **Comment asks**: you post → three followers and one other queue up
  (10–30 s apart). `approve` writes a real comment; `skip` means
  nothing ever happened — no call is made. Only for your own posts.
- **Per-persona models**: `set_model` stores a model per persona; every
  write resolves persona model → world model → default.

## Notes

- An abandoned world (no sync for 30 days) stops ticking — it sleeps
  until someone syncs again, and then it stirs within a minute.
- The nightly APK workflow is untouched: this branch builds nothing the
  phone installs. `.github/workflows/server.yml` type-checks and
  dry-run deploys this folder on every push to `world-server`.
