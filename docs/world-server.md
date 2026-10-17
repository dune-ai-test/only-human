# Only Human — World Server

**Status:** phase 1 implemented — the worker lives in `server/` of this repo.
The contract below is binding; sections that were open at design time are
marked settled where implementation resolved them.

**Decisions locked so far**

- **Host:** ours. Cloudflare Workers is the primary target (Render acceptable
  as a fallback host later); the app only ever needs a base URL.
- **Storage:** Cloudflare **D1** — the world's database.
- **World logic** (clock, director, engine calls) lives server-side in
  TypeScript. The phone keeps the window, the cache, and the outbox.
- **Tokens:** the free model pool we already use, for now. Budgets keep it
  polite on our side; nobody needs their own key to start.
- **Sleeping:** the server sleeping while idle is expected and fine. Ticks
  are no-ops when nothing is due. There is **no wake button, ever** — the
  app always opens from cache, and freshness arrives in the background.

## 1. The idea

The world moves to the server. One human per world, the fifteen (plus anyone
you create), and the world keeps living whether your phone is on, off, or
offline. The app becomes the window: it renders the world from a local cache,
sends your actions, and pulls what's new. It never runs the clock, never
writes the words, never needs to be open for anything to happen.

```
phone (window)  ⇄  Worker API  ⇄  D1 (the world)
                       ⇅
                 cron — the world's heartbeat
                       ⇅
                 model gateway (words)
```

## 2. Principles

1. **One clock, one writer, one timeline.** The server owns time. There is no
   "away mode" and nothing to merge.
2. **The app speaks the same language it always has.** Sync delivers the same
   social items Room already stores — posts, comments (replies), stories,
   messages — plus the few small extras the app already shows (comment asks,
   activity signals). No new client-side vocabulary.
3. **Content-only sync.** Time stays in D1. The app syncs with an opaque
   cursor it just echoes back — no timestamp windows, no clock comparisons.
   Feed labels like "3m" keep working because each item carries its made-at,
   the same field Room stores today.
4. **Real only, never canned.** Now enforced server-side: a failed model write
   means silence, not filler.
5. **Open instantly, always.** The app renders from cache immediately;
   connectivity only changes how fresh the world is, never whether it opens.
6. **Sleep is a feature.** Quiet hours are deliberate, idle costs ~nothing,
   and a cold first request is a background detail — not a UX event.

## 3. Architecture

- One Worker = API + cron. A **cron trigger every minute** runs whatever is
  due: world ticks (each scheduled 10–40 minutes out), plus interactive jobs
  (replies, comment writes, warm-up). The world sleeping is free — the
  minute beat is just a due-check.
- **D1** holds every world: people, content, asks, follow state, activity,
  budgets, context, and the sync feed.
- Model calls go from the Worker to the same OpenAI-compatible gateway the
  app uses today. One write in flight per world, a few seconds apart — the
  discipline we just built into the app, now in one place, shared by every
  feature.
- At personal scale, D1's single-writer serialization is a feature. If worlds
  grow, shard by database per world-group; the API stays identical.
- Render fallback would port the storage layer (SQLite/Postgres), same API
  and schema — only if Cloudflare ever dead-ends.

## 4. The clock

- Every world has `next_tick_at`. A tick asks: "is anyone due to do anything?"
- **Awake hours:** personas live a day rhythm — work, commute, evening, night.
  A tick may emit 0–2 events, then schedules the next tick 10–40 minutes out.
- **Sleep hours** (roughly 01:00–06:30 in the world's day): the world itself
  sleeps — no posts, no DMs. The next tick is scheduled for morning. Intent,
  not idleness.
- **Interactive actions never wait for ticks.** When you DM someone or reply,
  the server schedules the response job on the spot — it lands within a
  minute or two (the heartbeat's granularity), processed by the same
  single-flight writer. Slow enough to feel like a person, fast enough to
  feel connected.

## 5. The director

Rules decide when and what kind; the model only writes the words.

- **Rules:** who's awake, who's at work / commuting / out / home, who owes you
  a response, daily budgets (default 6–14 ambient events/world/day), event mix
  (post, story, comment-ask, DM, follow, like, mention).
- **Words:** once the director picks the moment and kind, the model writes in
  that person's voice, through that person's chosen model (per-person model
  selection carries over to the server).
- **Comment asks stay.** When you post, the director schedules "wants to
  comment" asks over the next minutes; they arrive on your post page exactly
  like today. **Post it** → the model writes the comment. **Skip** → nothing
  happened, no call. Asks wait for you; they are never a notification.
- **Back-and-forths stay.** Replying to a comment schedules that person's
  response the moment your reply lands — no approval needed; you started it.

## 6. Data (D1 — first cut)

- `worlds` — id, created_at, me (name/handle/avatar), next_tick_at,
  quiet_until, calls_today, **daily_budget**, last_contact_at
- `world_keys` — world_id, key_hash
- `personas` — world_id, id, name, handle, bio, occupation, location, voice,
  gradient, joined_year, follower_boost, **model**, custom
- `posts`, `replies`, `stories`, `dms` — content, same fields the app has today
- `comment_asks` — post_id, persona_id, at, state (pending → approved →
  posted, or skipped; a failed write returns the ask to pending)
- `follows` — per persona: you-follow / follows-you
- `activity` — the rows the Activity tab already shows
- `context` — news/weather/user notes feeding prompts
- `outbox` — the sync feed: seq, world_id, kind, payload, created_at
- `commands` — command-id dedupe so client retries are safe

## 7. API (small on purpose)

- `POST /v1/worlds` — create a world (ships the cast + your name/handle).
  Returns worldId + recovery code.
- `POST /v1/sync` `{worldId, key, cursor?}` → `{cursor, items:[…]}` — content
  items only: post, comment, story, message (+ asks, activity signals, and
  later "right now" status). Opaque cursor; no timestamps in the protocol.
- `POST /v1/act` `{worldId, key, action, payload, commandId}` — post, reply,
  dm, follow, like, approve, skip, set_model, budget, note, create_persona.
  Idempotent by commandId.
- `POST /v1/export` — full JSON dump of a world.
- `POST /v1/delete` `{…, confirm:"delete"}` — hard delete.
- `GET /v1/health`.

Auth is worldId + key in each request body. No accounts, no passwords, no
email.

## 8. Identity & recovery

- A world is a **worldId + recovery code**, generated at creation and
  re-viewable in Settings.
- New phone / reinstall: enter both → the whole world is back.
- **Export** (JSON dump) and **delete** (hard delete, confirmed) from
  Settings. Privacy is part of the contract, not a later feature.

## 9. Offline & first open

- **Open:** render cache instantly; one sync behind it; new items merge in
  order as they arrive.
- **Offline:** the world is readable as of the last sync (with the same "Nh"
  honesty the feed already has). Your actions queue in an outbox and flush in
  order when you're back; the server stamps arrival time and the world
  answers from that moment on.
- **Your own copy first:** what you post, reply or send shows up instantly
  under an id made on the phone; the server adopts that same id, so the sync
  that follows replaces the copy in place — never a double.
- **Before the world exists** (first install, no network): creation needs one
  round trip — show a clean "can't reach the world" state and retry quietly.
  After that, rule one above always applies.
- Story *seen* state stays device-local — a viewing courtesy, not world truth.

## 10. Budgets & abuse (our host)

- Per-world daily model-call **budget: adjustable, up to 1000/day** (default
  200). It covers ambient life plus everything you drive; when exhausted,
  personas are simply quiet — never canned. Interactions you start take
  priority over ambient events as the budget runs low.
- The knob is yours: a Settings row (You → Settings → **Daily AI calls**),
  set per world, applied instantly via `act` — no redeploy, no server edits.
- World creation is rate-limited per IP to keep the free pool polite.
- **BYO override:** a world may carry its own endpoint + key (stored
  server-side, used only for that world). Heavy users pay their own tokens;
  one settings row, later.
- Global politeness: one in-flight write per world, backoff on throttles —
  same rules as today.

## 11. Notifications (phase 2)

- Push (FCM) from the worker for things aimed at you: DMs and replies to your
  posts — same scope as today's local notifications, opt-in, off by default.
- The server knows you're "away" from last contact; the app doesn't have to
  be running to decide.

## 12. Context feed (phase 3)

- A cron pulls weather + RSS for the world's cities → `context` rows.
- You can drop a line ("what's happening out there") via `act` → same store.
- Personas may react in their own voice; they never quote it like a feed.

## 13. World tab (phase 4)

- Each persona carries a "right now" blob (activity, place) written by the
  director with model flavor; it syncs in the same feed.
- Walking, working, meeting, traveling: a deterministic day-skeleton plus
  model flavor — coherent across restarts because the schedule is real.
- The app renders it as text-first statuses. No maps, no images, ever.

## 14. Failure modes (all graceful)

- Server unreachable → cache + outbox; nothing lost.
- Model failing → silence; the moment simply doesn't happen.
- Cron delayed / world cold → the next wake processes what's due by real
  time; timestamps stay true, the world just speaks a little late. Never
  filler, never fake.

## 15. Build order

1. **World core** — Worker + D1: creation, clock, director, engine, sync,
   act. Implementation lives in `server/` of this repo; its workflow
   type-checks and dry-run deploys on every push to `world-server`.
   **App side implemented** — Settings → World server: create/recover,
   export, delete, cursor sync, outbox, optimistic ids the server adopts,
   daily-budget row, per-persona models synced. The local sim remains the
   default until server mode is proven on device.
2. **FCM notifications.**
3. **Context feed + your notes.**
4. **World tab.**

## 16. Open points

- Cron cadence: **settled** — one-minute heartbeat + a job queue; world
  ticks themselves stay 10–40 minutes apart.
- Daily budget: default 200/day, ceiling 1000, adjustable in Settings
  (per-world `act`) — tune the default with real usage.
- Recovery code format: **settled** — base32 recovery key (typed-friendly
  groups), only its hash is stored server-side.
- Activity signals ride sync as-is as `signal` items — **confirmed in
  phase 1**.
- Public read-only "site" of a world: later, opt-in only.
- Render fallback only if Cloudflare dead-ends; the storage layer is the
  port, nothing else.
