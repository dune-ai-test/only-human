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
  schema.sql           the whole D1 schema (run once per database)
  src/
    index.ts           fetch router + scheduled heartbeat
    web.ts             the site: landing + the console window (pure HTML, no build)
    api.ts             /v1/worlds, /v1/sync, /v1/act, /v1/presence, /v1/export, /v1/delete
    presence.ts        where everyone is: one batched write, cached in context
    director.ts        the clock: ticks, warm-up, replies, comment asks
    engine.ts          the one writer — retries, backoff, daily budget
    prompts.ts         persona / DM prompts (faithful port of the app's Engine.kt)
    db.ts              D1 helpers + the outbox
    util.ts            ids, recovery keys, world-local time
  scripts/smoke.mjs    end-to-end smoke test against a running worker
```

`wrangler.toml` carries the deployed Worker's shape — the D1 binding and
the every-minute cron. It has to live in the repo: `wrangler deploy`
replaces the Worker's bindings with whatever the deploy config says
(there is no `--keep-bindings` flag), so a binding added only in the
dashboard is erased by the next build. The model variables stay in the
dashboard; `keep_vars` keeps deploys from touching them. Local dev uses
an untracked `wrangler.local.toml` with a dummy database id — see
[Local dev](#local-dev).

## The site

The Worker serves its own small frontend — same palette and type scale as
the app, no assets, no build step:

- `GET /` — the landing: what this server is, in as few words as possible.
- `GET /w` — the window: paste a world id + recovery key and you're looking
  at the world itself — the world clock, a timeline of everything that
  happens, a composer, the ask queue, messages, the people, a **Now** view
  (where everyone is and what they're doing at this minute), and a **log**:
  one line per thing anyone did, in order (posts, stories, messages,
  comments, likes, follows, asks), with world-local times. Reads *and*
  writes through the same authenticated API the app uses. Credentials stay
  in that browser only; auto-refreshes every 40 s; writes are queued locally
  and retried until they land.
- Deep link: `GET /w#world=<id>&key=<key>&tab=<feed|now|log|dms|people>&persona=<id>&person=<id>`
  — the fragment never leaves the browser and is scrubbed from the address
  bar on load. `person` opens that person's sheet; `persona` opens their DM.

World data is only ever moved through the authenticated `POST /v1/sync` and
`POST /v1/act`, so the public pages expose nothing.

## Deploy (once) — the dashboard way (no CLI)

Everything happens on dash.cloudflare.com and github.com; the same account
owns the database and the Worker. Free plan is fine.

`wrangler.toml` in this folder declares the Worker name, the D1 binding
`DB` and the cron heartbeat, so each deploy restores them. The two model
defaults are **dashboard** variables (`keep_vars` protects them).

1. **Create the database.** *Storage & Databases → D1 SQL database → Create
   database* → name it `only-human-worlds` (no ID to copy — the binding in
   step 4 picks it from a list).
2. **Create the tables.** Open the database → **Console** tab → paste the
   whole of `server/schema.sql` → Execute. The file deliberately contains
   **no `--` comments**: the dashboard console flattens the SQL onto one
   line, and a comment would swallow the rest of its statement (SQLite then
   reports `incomplete input`). Every statement is `IF NOT EXISTS`, so
   running it twice is harmless. The **Tables** tab should list all 15:
   `worlds`, `world_keys`, `personas`, `relations`, `posts`, `replies`,
   `stories`, `dms`, `comment_asks`, `follows`, `activity`, `context`,
   `outbox`, `jobs`, `commands`.
3. **Create the Worker from the repo.** Cloudflare dashboard → *Workers &
   Pages → Create application → Get started next to "Import a repository"*
   → connect GitHub → pick `dune-ai-test/only-human`, then set:
   - Project name: `only-human-worlds`
   - Production branch: `world-server`
   - Build command: leave empty
   - Deploy command: `npx wrangler deploy src/index.ts --name only-human-worlds --compatibility-date 2026-10-18 --keep-vars`
   - Root directory: `server` (under the advanced/build settings)

   Save and Deploy. The build log ends with the live URL —
   `https://only-human-worlds.<you>.workers.dev`. From then on, every push
   to `world-server` rebuilds and redeploys automatically — docs-only edits
   included (pushes to `main` do not). Progress shows under the Worker's
   **Deployments** tab.
4. **The database binding ships in `wrangler.toml`** (`DB` →
   `only-human-worlds`, id `b1f2dcfd-…`). Do not add a second binding in
   the dashboard: deploys replace dashboard bindings with the repo config.
   If step 7 reports `db:false`, the database id in `wrangler.toml` is the
   thing to check.
5. **Model defaults.** **Settings → Variables and Secrets** — two *text*
   variables: `DEFAULT_ENDPOINT` = `https://api.kilo.ai/api/gateway` and
   `DEFAULT_MODEL` = `poolside/laguna-s-2.1:free`. `keep_vars` (and
   `--keep-vars` in the deploy command) means deploys never touch them.
6. **Heartbeat.** Also in `wrangler.toml`: `* * * * *`. Deploys attach it;
   it should show under **Settings → Triggers → Cron Triggers** after the
   first build.
7. **Check it's alive.** Open the URL (the landing page shows), then
   `/v1/health` — expect `"ok":true,"db":true` and
   `"vars":{"endpoint":true,"model":true}`. The Worker's Logs show a
   scheduled event every minute. After a new world is created, the first
   warm-up posts appear within a minute.

Then connect the app: Settings → World server → paste the URL → "Create the
world". The landing page confirms the server is awake; `/w` opens the
console window.

### …or with wrangler (if you ever prefer the CLI)

The database (steps 1–2) still comes from the dashboard. For the Worker
itself you can skip the Git connection entirely — `wrangler.toml` supplies
the binding and the cron, `npm run deploy` ships it:

```sh
cd server
npm install
npx wrangler login
npm run deploy
```

Only the two model variables are added in the dashboard (step 5).

No secrets needed with the default keyless model
(`poolside/laguna-s-2.1:free` on the Kilo gateway). A per-world custom
endpoint/model/key can be added to the `worlds` row later for BYO users.

## Local dev

Local dev uses its own config and a local database. Create
`server/wrangler.local.toml` once (it is gitignored; the dummy
`database_id` is fine against the local D1). `wrangler.toml` is the
production config and is never used by the dev script:

```toml
name = "only-human-worlds"
main = "src/index.ts"
compatibility_date = "2026-10-18"

[triggers]
crons = ["* * * * *"]

[vars]
DEFAULT_ENDPOINT = "https://api.kilo.ai/api/gateway"
DEFAULT_MODEL = "poolside/laguna-s-2.1:free"

[[d1_databases]]
binding = "DB"
database_name = "only-human-worlds"
database_id = "00000000-0000-0000-0000-000000000000"
```

Then:

```sh
npm run schema:local     # creates the local D1 with the schema
npm run dev              # http://127.0.0.1:8787
node scripts/smoke.mjs http://127.0.0.1:8787 --pulse   # creates a world, posts, approves an ask
```

To fire the every-minute heartbeat by hand: `npm run dev -- --test-scheduled`,
then `curl "http://127.0.0.1:8787/__scheduled?cron=*+*+*+*+*"`.

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
- `POST /v1/presence` `{ worldId, key, personaId?, force? }` — who is
  where, right now. Refreshes only the people whose cached state is older
  than 15 minutes, in one batched write; `personaId` asks about one person,
  `force` refreshes now. Returns `{ at, people: [{ id, place, doing, at,
  stale }], budget }`.
- `POST /v1/export` — full JSON dump of the world.
- `POST /v1/delete` `{ …, confirm: "delete" }` — hard delete.

## How the world runs

- **Heartbeat**: a cron trigger every minute runs due jobs: world ticks
  (scheduled 5–14 min apart while awake; a long silence forces an event),
  warm-up writes, DM replies, comment asks and comment writes. Interactive
  things land roughly a minute or two after you do them — slow enough to
  feel human.
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
- **Presence**: the Now view keeps a model-written "where and what" for
  each person. It is cached in the `context` table for 15 minutes; opening
  the view or a person's sheet refreshes the stale half in **one** batched
  call (never one call per person). At night most of the cast is asleep.
  A failed call leaves the previous state with its real age — it never
  invents a location. No schema change: presence lives in `context`.

## Notes

- An abandoned world (no sync for 30 days) stops ticking — it sleeps
  until someone syncs again, and then it stirs within a minute.
- The nightly APK workflow is untouched: this branch builds nothing the
  phone installs. `.github/workflows/server.yml` type-checks and
  dry-run deploys this folder on every push to `world-server`.
