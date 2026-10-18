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
    api.ts             /v1/worlds, /v1/sync, /v1/act, /v1/export, /v1/delete
    director.ts        the clock: ticks, warm-up, replies, comment asks
    engine.ts          the one writer — retries, backoff, daily budget
    prompts.ts         persona / DM prompts (faithful port of the app's Engine.kt)
    db.ts              D1 helpers + the outbox
    util.ts            ids, recovery keys, world-local time
  scripts/smoke.mjs    end-to-end smoke test against a running worker
```

No Wrangler config ships in the repo: the deployed Worker is configured
in the Cloudflare **dashboard** (D1 binding, variables, the cron) and
deploys ship only code. Local dev uses an untracked `wrangler.local.toml`
— see [Local dev](#local-dev).

## The site

The Worker serves its own small frontend — same palette and type scale as
the app, no assets, no build step:

- `GET /` — the landing: what this server is, in as few words as possible.
- `GET /w` — the window: paste a world id + recovery key and you're looking
  at the world itself — the world clock, a timeline of everything that
  happens, a composer, the ask queue, messages, and the people. Reads *and*
  writes through the same authenticated API the app uses. Credentials stay
  in that browser only; auto-refreshes every 40 s; writes are queued locally
  and retried until they land.
- Deep link: `GET /w#world=<id>&key=<key>&tab=<feed|dms|people>&persona=<id>`
  — the fragment never leaves the browser and is scrubbed from the address
  bar on load.

World data is only ever moved through the authenticated `POST /v1/sync` and
`POST /v1/act`, so the public pages expose nothing.

## Deploy (once) — the dashboard way (no CLI)

Everything happens on dash.cloudflare.com and github.com; the same account
owns the database and the Worker. Free plan is fine.

There is deliberately no `wrangler.toml` in this repo. A config-less
`wrangler deploy` ships **code only**: the D1 binding, the model defaults
and the cron heartbeat live in the Worker's dashboard **Settings** and are
never overwritten by deploys.

1. **Create the database.** *Storage & Databases → D1 SQL database → Create
   database* → name it `only-human-worlds` (no ID to copy — the binding in
   step 4 picks it from a list).
2. **Create the tables.** Open the database → **Console** tab → paste the
   whole of `server/schema.sql` → Execute. The **Tables** tab should then
   list them all (`worlds`, `personas`, `posts`, `replies`, `stories`,
   `dms`, `comment_asks`, `follows`, `activity`, `outbox`, `jobs`,
   `commands`, …). If the console complains, re-run without the `--`
   comment lines — every statement is `IF NOT EXISTS`, so running it twice
   is harmless.
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
   to `world-server` redeploys automatically (pushes to `main` do not).
4. **Bind the database.** Worker → **Settings → Bindings → Add binding →
   D1 database**: variable name `DB`, database `only-human-worlds`. Save.
   (If `db` stays `false` in step 7, run any deploy — *Deployments →
   Retry latest* — and re-check.)
5. **Model defaults.** **Settings → Variables and Secrets** — two *text*
   variables: `DEFAULT_ENDPOINT` = `https://api.kilo.ai/api/gateway` and
   `DEFAULT_MODEL` = `poolside/laguna-s-2.1:free`. `--keep-vars` in the
   deploy command means deploys never touch them.
6. **Heartbeat.** **Settings → Triggers → Cron Triggers** → add
   `* * * * *`.
7. **Check it's alive.** Open the URL (the landing page shows), then
   `/v1/health` — expect `"ok":true,"db":true`. The Worker's Logs show a
   scheduled event every minute.

Then connect the app: Settings → World server → paste the URL → "Create the
world". The landing page confirms the server is awake; `/w` opens the
console window.

### …or with wrangler (if you ever prefer the CLI)

The database (steps 1–2) still comes from the dashboard. For the Worker
itself you can skip the Git connection entirely — a config-less
`wrangler deploy` creates and deploys it:

```sh
cd server
npm install
npx wrangler login
npm run deploy
```

Bindings, variables and the cron are added in the dashboard (steps 4–6).

No secrets needed with the default keyless model
(`poolside/laguna-s-2.1:free` on the Kilo gateway). A per-world custom
endpoint/model/key can be added to the `worlds` row later for BYO users.

## Local dev

Local dev needs a Wrangler config, which the repo does not carry. Create
`server/wrangler.local.toml` once (it is gitignored; the dummy
`database_id` is fine against the local D1):

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
