/**
 * The HTTP contract, exactly as docs/world-server.md describes it:
 *   POST /v1/worlds  — create a world (ships the cast + your name/handle)
 *   POST /v1/sync    — pull everything since the cursor
 *   POST /v1/act     — post, reply, dm, follow, like, approve, skip, …
 *   POST /v1/export  — a JSON dump of everything
 *   POST /v1/delete  — hard delete, with confirm
 */

import {
  getFollows,
  getNotes,
  getPersona,
  getPersonas,
  insertDm,
  insertPost,
  insertReply,
  push,
  pushActivity,
  pushPostUpdate,
  resetDayIfNeeded,
  schedule,
  setFollow,
} from "./db";
import { addCustomOrg, addPresetOrg, ensureNewsSchema, ensureSeedOrgs, getOrg, getOrgs, orgPayload } from "./news";
import { getPresence } from "./presence";
import { draftOrgAsk, draftPersonAsk } from "./prompts";
import { writeRaw } from "./engine";
import type { AskRow, Env, PostRow, ReplyRow, WorldRow } from "./types";
import { clamp, dayKey, hashKey, newId, newRecoveryKey, now, QUIET_FROM_MIN, QUIET_TO_MIN, randInt, sameHash, json } from "./util";

function str(v: unknown): string {
  return typeof v === "string" ? v : "";
}

/**
 * An id the phone made up for its own optimistic copy. Accepted only if it's
 * plausible — anything odd gets a server-minted id instead.
 */
function clientId(v: unknown): string | null {
  const clean = typeof v === "string" ? v.trim() : "";
  return /^[A-Za-z0-9_-]{6,64}$/.test(clean) ? clean : null;
}

function shuffled<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = randInt(0, i);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

interface ParsedPersona {
  id: string;
  name: string;
  handle: string;
  bio: string;
  occupation: string;
  location: string;
  voice: string;
  gradient: string;
  joinedYear: string;
  followerBoost: number;
  custom: number;
  relation: string;
}

function parsePersona(raw: Record<string, unknown>, fallbackId: string, custom = false, meName = ""): ParsedPersona | null {
  const name = str(raw["name"]).trim();
  const handle = str(raw["handle"]).replace(/^@+/, "").trim();
  if (!name || !handle) return null;
  const gradient = Array.isArray(raw["gradient"])
    ? (raw["gradient"] as unknown[]).filter((c): c is string => typeof c === "string").slice(0, 4)
    : [];
  const boost = Number(raw["followerBoost"]);
  return {
    id: str(raw["id"]).trim() || fallbackId,
    name,
    handle,
    bio: clipLine(raw["bio"], 240),
    occupation: clipLine(raw["occupation"], 80),
    location: clipLine(raw["location"], 60),
    voice: clipLine(raw["voice"], 240),
    gradient: JSON.stringify(gradient),
    joinedYear: str(raw["joinedYear"]).trim(),
    followerBoost: Number.isFinite(boost) ? Math.round(boost) : 0,
    custom: custom ? 1 : 0,
    relation: cleanRelation(raw["relation"], meName),
  };
}

/** One flat line, cut at a word boundary when long. */
function clipLine(value: unknown, max: number): string {
  const s = str(value).replace(/[\r\n]+/g, " ").replace(/\s+/g, " ").trim();
  if (s.length <= max) return s;
  const head = s.slice(0, max);
  const space = head.lastIndexOf(" ");
  return (space > max * 0.5 ? head.slice(0, space) : head).replace(/[,;:.\-–—]+$/, "").trimEnd();
}

/** "Who they are to you" — the human's own words ("my wife"), one short phrase. */
function cleanRelation(value: unknown, meName = ""): string {
  let s = str(value).replace(/[\r\n"]+/g, " ").replace(/\s+/g, " ").trim();
  if (!s) return "";
  if (meName) {
    const first = meName.trim().split(/\s+/)[0].toLowerCase();
    for (const marker of [`${meName.trim().toLowerCase()}'s`, `${first}'s`]) {
      const at = s.toLowerCase().indexOf(marker);
      if (at >= 0) {
        const after = s
          .slice(at + marker.length)
          .trim()
          .split(/\s+/)[0]
          .replace(/[^\p{L}\p{N}-]/gu, "");
        if (after) return `my ${after.toLowerCase()}`.slice(0, 40);
      }
    }
  }
  s = s.replace(/^(?:i'?m|i am)\s+[^,]*[, ]*/i, "").trim();
  s = s.split(/\s+(?:and|but)\s+|[.;]/i)[0].trim();
  s = s.replace(/[,;:]+$/, "").trim();
  return s.slice(0, 40);
}

// ---- profiles written from a human description --------------------------

const DRAFT_LIMIT = 700;

function cleanHandle(value: unknown): string {
  return str(value)
    .trim()
    .replace(/^@+/, "")
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "")
    .slice(0, 30);
}

function handleFromName(name: string): string {
  return name
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "")
    .slice(0, 24);
}

async function uniqueHandle(env: Env, world: WorldRow, kind: "person" | "org", wanted: string): Promise<string> {
  const base = wanted || "neighbor";
  const personas = await env.DB.prepare("SELECT handle FROM personas WHERE world_id = ?")
    .bind(world.id)
    .all<{ handle: string }>();
  let orgHandles: string[] = [];
  try {
    await ensureNewsSchema(env);
    const orgs = await env.DB.prepare("SELECT handle FROM orgs WHERE world_id = ? AND active = 1")
      .bind(world.id)
      .all<{ handle: string }>();
    orgHandles = orgs.results.map((o) => o.handle);
  } catch {
    orgHandles = [];
  }
  const taken = new Set([...personas.results.map((p) => p.handle), ...orgHandles].map((h) => h.toLowerCase()));
  if (!taken.has(base.toLowerCase())) return base;
  for (let i = 2; i < 100; i++) {
    const next = `${base}${i}`.slice(0, 30);
    if (!taken.has(next.toLowerCase())) return next;
  }
  return `${base}${Date.now().toString(36)}`.slice(0, 30);
}

interface DraftPerson {
  name: string;
  handle: string;
  occupation: string;
  city: string;
  bio: string;
  voice: string;
  relation: string;
}

interface DraftOrg {
  name: string;
  handle: string;
  city: string;
  bio: string;
  monogram: string;
  color: string;
}

type Draft = DraftPerson | DraftOrg;

function parseDraft(raw: string, kind: "person" | "org", meName = ""): Draft | null {
  const text = raw.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  let obj: Record<string, unknown>;
  try {
    const parsed = JSON.parse(text.slice(start, end + 1)) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
    obj = parsed as Record<string, unknown>;
  } catch {
    return null;
  }
  if (str(obj["error"]).trim()) return null;

  const name = clipLine(obj["name"], 80);
  if (!name) return null;
  const handle = cleanHandle(obj["handle"]) || handleFromName(name);
  if (!handle) return null;
  const city = clipLine(obj["city"], 60);
  const bio = clipLine(obj["bio"], 200);

  if (kind === "person") {
    const occupation = clipLine(obj["occupation"], 80);
    const voice = clipLine(obj["voice"], 200);
    const relation = cleanRelation(obj["relation"], meName);
    return { name, handle, occupation, city, bio, voice, relation };
  }

  const monogram =
    str(obj["monogram"]).replace(/[^A-Za-z0-9]/g, "").toUpperCase().slice(0, 4) ||
    name.replace(/[^\p{L}\p{N} ]/gu, " ").split(/\s+/).filter(Boolean).slice(0, 4).map((w) => w[0]).join("").toUpperCase();
  const color = /^#[0-9a-f]{6}$/i.test(str(obj["color"])) ? str(obj["color"]) : "";
  return { name, handle, city, bio, monogram, color };
}

function publicFeedUrl(raw: string): string {
  const value = raw.trim().slice(0, 600);
  try {
    const url = new URL(value);
    if (url.protocol !== "http:" && url.protocol !== "https:") return "";
    const host = url.hostname.toLowerCase();
    if (!host || host === "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return "";
    if (/^(127\.|10\.|0\.|169\.254\.|192\.168\.)/.test(host)) return "";
    if (/^172\.(1[6-9]|2\d|3[01])\./.test(host)) return "";
    const bare = host.replace(/^\[|\]$/g, "");
    if (bare.includes(":") && (bare === "::1" || bare.startsWith("fc") || bare.startsWith("fd") || bare.startsWith("fe80"))) {
      return "";
    }
    return url.toString();
  } catch {
    return "";
  }
}

function parseCustomOrg(raw: Record<string, unknown>): {
  name: string;
  handle: string;
  city: string;
  bio: string;
  feedUrl: string;
  monogram: string;
  color: string;
} | null {
  const name = str(raw["name"]).replace(/\s+/g, " ").trim().slice(0, 80);
  const handle = cleanHandle(raw["handle"]);
  const city = str(raw["city"]).replace(/\s+/g, " ").trim().slice(0, 60);
  const bio = str(raw["bio"]).replace(/\s+/g, " ").trim().slice(0, 240);
  const feedUrl = publicFeedUrl(str(raw["feedUrl"]));
  if (!name || !handle || !feedUrl) return null;
  return {
    name,
    handle,
    city,
    bio,
    feedUrl,
    monogram: str(raw["monogram"]).replace(/[^A-Za-z0-9]/g, "").toUpperCase().slice(0, 4),
    color: str(raw["color"]),
  };
}

async function authenticate(env: Env, body: Record<string, unknown>): Promise<WorldRow | null> {
  const worldId = str(body["worldId"]);
  const key = str(body["key"]);
  if (!worldId || !key) return null;
  const world = await env.DB.prepare("SELECT * FROM worlds WHERE id = ?").bind(worldId).first<WorldRow>();
  if (!world) return null;
  const row = await env.DB.prepare("SELECT key_hash FROM world_keys WHERE world_id = ?")
    .bind(worldId)
    .first<{ key_hash: string }>();
  if (!row) return null;
  const hash = await hashKey(key);
  if (!sameHash(hash, row.key_hash)) return null;
  return world;
}

// ---- create --------------------------------------------------------------

export async function handleCreate(env: Env, body: Record<string, unknown>): Promise<Response> {
  const me = (body["me"] ?? {}) as Record<string, unknown>;
  const meName = str(me["name"]).trim();
  const meHandle = str(me["handle"]).replace(/^@+/, "").trim();
  if (!meName || !meHandle) return json({ error: "name and handle required" }, 400);

  const avatarRaw = Number(me["avatarIndex"]);
  const avatar = Number.isFinite(avatarRaw) ? clamp(Math.round(avatarRaw), 0, 63) : 0;
  const offsetRaw = Number(body["utcOffsetMinutes"]);
  const offset = Number.isFinite(offsetRaw) ? clamp(Math.round(offsetRaw), -840, 840) : 0;

  const rawPersonas = Array.isArray(body["personas"]) ? (body["personas"] as Record<string, unknown>[]) : [];
  const parsed = rawPersonas
    .map((p, i) => parsePersona(p, `p${i}`))
    .filter((p): p is ParsedPersona => p !== null)
    .slice(0, 64);
  if (parsed.length === 0) return json({ error: "at least one persona required" }, 400);

  const rawRelations = Array.isArray(body["relations"]) ? (body["relations"] as Record<string, unknown>[]) : [];
  const relations = rawRelations
    .map((r) => ({ a: str(r["a"]).trim(), b: str(r["b"]).trim(), label: str(r["label"]).trim() }))
    .filter((r) => r.a && r.b && r.label)
    .slice(0, 200);

  const following = (body["following"] ?? {}) as Record<string, unknown>;
  const followedBy = (body["followedBy"] ?? {}) as Record<string, unknown>;
  const known = new Set(parsed.map((p) => p.id));
  const followIds = [...new Set([...Object.keys(following), ...Object.keys(followedBy)])].filter((id) =>
    known.has(id),
  );

  const worldId = crypto.randomUUID();
  const key = newRecoveryKey();
  const keyHash = await hashKey(key);
  const t = now();

  const stmts: D1PreparedStatement[] = [
    env.DB.prepare(
      "INSERT INTO worlds (id, created_at, utc_offset, me_name, me_handle, me_avatar, next_tick_at, quiet_until, calls_today, calls_day, daily_budget, last_contact_at) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, ?, 200, ?)",
    ).bind(worldId, t, offset, meName, meHandle, avatar, dayKey(t, offset), t),
    env.DB.prepare("INSERT INTO world_keys (world_id, key_hash) VALUES (?, ?)").bind(worldId, keyHash),
  ];

  const personaInsert =
    "INSERT OR IGNORE INTO personas (world_id, id, name, handle, bio, occupation, location, voice, gradient, joined_year, follower_boost, model, custom) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)";
  for (const p of parsed) {
    stmts.push(
      env.DB.prepare(personaInsert).bind(
        worldId,
        p.id,
        p.name,
        p.handle,
        p.bio,
        p.occupation,
        p.location,
        p.voice,
        p.gradient,
        p.joinedYear,
        p.followerBoost,
        p.custom,
      ),
    );
  }
  for (const r of relations) {
    stmts.push(
      env.DB.prepare("INSERT INTO relations (world_id, a, b, label) VALUES (?, ?, ?, ?)").bind(
        worldId,
        r.a,
        r.b,
        r.label,
      ),
    );
  }
  for (const id of followIds) {
    stmts.push(
      env.DB.prepare(
        "INSERT OR IGNORE INTO follows (world_id, persona_id, following, followed_by) VALUES (?, ?, ?, ?)",
      ).bind(worldId, id, following[id] === true ? 1 : 0, followedBy[id] === true ? 1 : 0),
    );
  }
  await env.DB.batch(stmts);

  // A phone that adopts this world reads from cursor zero, so the starting
  // state ships as items too: anyone recovering on a fresh install sees the
  // same cast, customs and follows the creator saw.
  for (const p of parsed) {
    await push(env, worldId, "persona", {
      id: p.id,
      name: p.name,
      handle: p.handle,
      bio: p.bio,
      occupation: p.occupation,
      location: p.location,
      voice: p.voice,
      gradient: JSON.parse(p.gradient),
      joinedYear: p.joinedYear,
      followerBoost: p.followerBoost,
    });
  }
  for (const id of followIds) {
    await push(env, worldId, "follow", {
      personaId: id,
      following: following[id] === true,
      followedBy: followedBy[id] === true,
    });
  }

  // Warm-up: the neighbors are there before you are, and they notice you.
  // Staggered so the first minutes look like people waking up, not a batch.
  const warm = shuffled(parsed);
  let at = t + randInt(15_000, 30_000);
  const postCount = Math.min(6, Math.max(2, warm.length));
  for (let i = 0; i < postCount; i++) {
    await schedule(env, worldId, at, "write_post", { personaId: warm[i % warm.length].id });
    at += randInt(15_000, 40_000);
  }
  const storyCount = Math.min(2, warm.length);
  for (let i = 0; i < storyCount; i++) {
    await schedule(env, worldId, at, "write_story", { personaId: warm[(i + 3) % warm.length].id });
    at += randInt(20_000, 50_000);
  }
  const welcomer = warm.find((p) => followedBy[p.id] === true) ?? warm[0];
  if (welcomer) {
    await schedule(env, worldId, t + randInt(40_000, 70_000), "dm_open", { personaId: welcomer.id });
  }
  await schedule(env, worldId, t + randInt(5, 9) * 60_000, "tick", {});

  // A world starts with two real newsrooms; their square profiles and their
  // headlines travel through the same outbox as everything else.
  const world = await env.DB.prepare("SELECT * FROM worlds WHERE id = ?").bind(worldId).first<WorldRow>();
  if (world) {
    try {
      await ensureSeedOrgs(env, world);
    } catch (e) {
      console.log("org seed", world.id, String(e).slice(0, 200));
    }
  }

  return json({ worldId, key });
}

// ---- sync ----------------------------------------------------------------

export async function handleSync(env: Env, body: Record<string, unknown>): Promise<Response> {
  const world = await authenticate(env, body);
  if (!world) return json({ error: "unauthorized" }, 401);

  const t = now();
  const prevContact = world.last_contact_at;
  await env.DB.prepare("UPDATE worlds SET last_contact_at = ? WHERE id = ?").bind(t, world.id).run();
  await resetDayIfNeeded(env, world);
  try {
    await ensureSeedOrgs(env, world);
  } catch (e) {
    // Newsrooms are additive: a schema or seed hiccup must never keep the
    // rest of the world from syncing.
    console.log("org seed", world.id, String(e).slice(0, 200));
  }

  // Coming back after the world went to sleep: wake it.
  if (prevContact > 0 && t - prevContact > 30 * 86_400_000) {
    await schedule(env, world.id, t + 60_000, "tick", {});
  }

  const cursorRaw = Number(body["cursor"]);
  const cursor = Number.isFinite(cursorRaw) && cursorRaw > 0 ? Math.floor(cursorRaw) : 0;

  const rows = await env.DB.prepare(
    "SELECT seq, kind, payload, created_at FROM outbox WHERE world_id = ? AND seq > ? ORDER BY seq LIMIT 300",
  )
    .bind(world.id, cursor)
    .all<{ seq: number; kind: string; payload: string; created_at: number }>();

  const items: unknown[] = [];
  let last = cursor;
  for (const row of rows.results) {
    last = row.seq;
    // Stories from before yesterday aren't stories any more.
    if (row.kind === "story" && t - row.created_at > 86_400_000) continue;
    try {
      items.push({ seq: row.seq, kind: row.kind, at: row.created_at, data: JSON.parse(row.payload) });
    } catch {
      // A broken row is silence — never a crash.
    }
  }
  return json({
    cursor: last,
    items,
    // Who you are over there — recovery on a new phone adopts this.
    me: { name: world.me_name, handle: world.me_handle, avatarIndex: world.me_avatar },
    budget: world.daily_budget,
    // The window's clock and stats. `t` is the server's now, so the browser
    // clock can be corrected against it; the app ignores what it doesn't use.
    t,
    world: {
      id: world.id,
      createdAt: world.created_at,
      utcOffsetMinutes: world.utc_offset,
      nextTickAt: world.next_tick_at,
      quietFrom: QUIET_FROM_MIN,
      quietTo: QUIET_TO_MIN,
      callsToday: world.calls_today,
      callsDay: world.calls_day,
      dailyBudget: world.daily_budget,
    },
  });
}

// ---- act -----------------------------------------------------------------

export async function handleAct(env: Env, body: Record<string, unknown>): Promise<Response> {
  const world = await authenticate(env, body);
  if (!world) return json({ error: "unauthorized" }, 401);

  const action = str(body["action"]);
  const p = (body["payload"] ?? {}) as Record<string, unknown>;
  const commandId = str(body["commandId"]);
  if (commandId) {
    const ins = await env.DB.prepare(
      "INSERT OR IGNORE INTO commands (world_id, command_id, created_at) VALUES (?, ?, ?)",
    )
      .bind(world.id, commandId, now())
      .run();
    if ((ins.meta.changes ?? 0) === 0) return json({ ok: true, duplicate: true });
  }

  await resetDayIfNeeded(env, world);
  const t = now();

  switch (action) {
    case "post": {
      const text = str(p["text"]).trim();
      if (!text) return json({ error: "empty" }, 400);
      const postId = await insertPost(env, world, "you", text.slice(0, 500), clientId(p["id"]) ?? undefined);

      // The neighbors queue up: three who follow you, one who doesn't.
      const personas = await getPersonas(env, world.id);
      const follows = await getFollows(env, world.id);
      const followers = shuffled(personas.filter((x) => follows.get(x.id)?.followed_by === 1));
      const others = shuffled(personas.filter((x) => follows.get(x.id)?.followed_by !== 1));
      const chosen = [...followers.slice(0, 3), ...others.slice(0, 1)];
      let delay = 8_000;
      for (const asker of chosen) {
        delay += randInt(10_000, 30_000);
        await schedule(env, world.id, t + delay, "ask", { postId, personaId: asker.id });
      }
      return json({ ok: true, id: postId });
    }

    case "reply": {
      const postId = str(p["postId"]);
      const text = str(p["text"]).trim();
      if (!postId || !text) return json({ error: "empty" }, 400);
      const post = await env.DB.prepare("SELECT * FROM posts WHERE world_id = ? AND id = ?")
        .bind(world.id, postId)
        .first<PostRow>();
      if (!post) return json({ error: "no such post" }, 404);
      const replyId = await insertReply(env, world, postId, "you", text.slice(0, 500), clientId(p["id"]) ?? undefined);

      if (post.author_id === "you") {
        // You replied under your own post — the commenter comes back.
        const target = await env.DB.prepare(
          "SELECT * FROM replies WHERE world_id = ? AND post_id = ? AND author_id != 'you' ORDER BY created_at DESC LIMIT 1",
        )
          .bind(world.id, postId)
          .first<ReplyRow>();
        if (target && Math.random() <= 0.8) {
          await schedule(env, world.id, t + randInt(25_000, 90_000), "reply_back", {
            personaId: target.author_id,
            postId,
            mode: "commenter",
          });
        }
      } else if (Math.random() <= 0.65) {
        // You replied under their post — the author thinks of something.
        await schedule(env, world.id, t + randInt(25_000, 90_000), "reply_back", {
          personaId: post.author_id,
          postId,
          mode: "author",
        });
      }
      return json({ ok: true, id: replyId });
    }

    case "dm": {
      const personaId = str(p["personaId"]);
      const text = str(p["text"]).trim();
      if (!personaId || !text) return json({ error: "empty" }, 400);
      const persona = await getPersona(env, world.id, personaId);
      if (!persona) return json({ error: "no such persona" }, 404);
      const dmId = await insertDm(env, world, personaId, true, text.slice(0, 500), clientId(p["id"]) ?? undefined);
      await schedule(env, world.id, t + randInt(20_000, 60_000), "dm_reply", { personaId });
      return json({ ok: true, id: dmId });
    }

    case "follow": {
      const personaId = str(p["personaId"]);
      const on = p["on"] === true;
      const persona = (await getPersona(env, world.id, personaId)) ?? (await getOrg(env, world.id, personaId));
      if (!persona) return json({ error: "no such persona" }, 404);
      await setFollow(env, world, personaId, { following: on ? 1 : 0 });
      if (on && Math.random() <= 0.6) {
        // Following someone is a small door — sometimes they walk through.
        await schedule(env, world.id, t + randInt(2 * 60_000, 20 * 60_000), "follow_back", { personaId });
      }
      return json({ ok: true });
    }

    case "like": {
      const postId = str(p["postId"]);
      const post = await env.DB.prepare("SELECT * FROM posts WHERE world_id = ? AND id = ?")
        .bind(world.id, postId)
        .first<PostRow>();
      if (!post) return json({ error: "no such post" }, 404);
      const liked = post.liked_by_me === 1;
      await env.DB.prepare("UPDATE posts SET likes = ?, liked_by_me = ? WHERE world_id = ? AND id = ?")
        .bind(Math.max(0, post.likes + (liked ? -1 : 1)), liked ? 0 : 1, world.id, postId)
        .run();
      await pushPostUpdate(env, world.id, postId);
      return json({ ok: true, liked: !liked });
    }

    case "approve": {
      const askId = str(p["askId"]);
      const ask = await env.DB.prepare("SELECT * FROM comment_asks WHERE world_id = ? AND id = ?")
        .bind(world.id, askId)
        .first<AskRow>();
      if (!ask || ask.state !== "pending") return json({ ok: true });
      await env.DB.prepare("UPDATE comment_asks SET state = 'approved' WHERE world_id = ? AND id = ?")
        .bind(world.id, ask.id)
        .run();
      await push(env, world.id, "ask", {
        id: ask.id,
        postId: ask.post_id,
        personaId: ask.persona_id,
        at: ask.at,
        state: "approved",
      });
      await schedule(env, world.id, t + randInt(5_000, 20_000), "comment_write", { askId: ask.id });
      return json({ ok: true });
    }

    case "skip": {
      const askId = str(p["askId"]);
      const ask = await env.DB.prepare("SELECT * FROM comment_asks WHERE world_id = ? AND id = ?")
        .bind(world.id, askId)
        .first<AskRow>();
      if (!ask || (ask.state !== "pending" && ask.state !== "approved")) return json({ ok: true });
      await env.DB.prepare("UPDATE comment_asks SET state = 'skipped' WHERE world_id = ? AND id = ?")
        .bind(world.id, ask.id)
        .run();
      await push(env, world.id, "ask", {
        id: ask.id,
        postId: ask.post_id,
        personaId: ask.persona_id,
        at: ask.at,
        state: "skipped",
      });
      return json({ ok: true });
    }

    case "set_model": {
      const personaId = str(p["personaId"]);
      const model = str(p["model"]).trim() || null;
      if (!personaId) return json({ error: "personaId required" }, 400);
      await env.DB.prepare("UPDATE personas SET model = ? WHERE world_id = ? AND id = ?")
        .bind(model, world.id, personaId)
        .run();
      // Who writes in which voice is part of the world — let other phones know.
      await push(env, world.id, "model", { personaId, model });
      return json({ ok: true });
    }

    case "budget": {
      const value = Math.round(Number(p["value"]));
      if (!Number.isFinite(value)) return json({ error: "value required" }, 400);
      const budget = clamp(value, 0, 1000);
      await env.DB.prepare("UPDATE worlds SET daily_budget = ? WHERE id = ?").bind(budget, world.id).run();
      return json({ ok: true, budget });
    }

    case "note": {
      const text = str(p["text"]).trim();
      if (!text) return json({ error: "empty" }, 400);
      const noteId = newId("ctx");
      await env.DB.prepare(
        "INSERT INTO context (world_id, id, source, text, at, active) VALUES (?, ?, 'you', ?, ?, 1)",
      )
        .bind(world.id, noteId, text.slice(0, 1000), t)
        .run();
      return json({ ok: true, id: noteId });
    }

    case "note_remove": {
      const noteId = str(p["id"]).trim();
      if (!noteId) return json({ error: "id required" }, 400);
      // Retire it rather than delete it: the row stays as an honest record,
      // but it stops coloring prompts and disappears from the console.
      await env.DB.prepare(
        "UPDATE context SET active = 0 WHERE world_id = ? AND id = ? AND source = 'you'",
      )
        .bind(world.id, noteId)
        .run();
      return json({ ok: true });
    }

    case "draft_profile": {
      const kind: "person" | "org" = str(p["kind"]) === "org" ? "org" : "person";
      const description = str(p["description"]).replace(/\s+/g, " ").trim();
      if (!description) return json({ error: "describe them first" }, 400);
      if (description.length > DRAFT_LIMIT) {
        return json({ error: `keep it under ${DRAFT_LIMIT} characters` }, 400);
      }
      const people = await getPersonas(env, world.id);
      const handles = people.map((x) => x.handle);
      const names = people.map((x) => x.name);
      try {
        handles.push(...(await getOrgs(env, world.id)).map((x) => x.handle));
      } catch {
        // A missing news schema should not block a person draft.
      }
      const ask = kind === "person" ? draftPersonAsk(world, description, handles, names) : draftOrgAsk(description, handles);
      const raw = await writeRaw(env, world, ask.system, [{ role: "user", content: ask.user }], 500, null, false, 0.55);
      if (!raw) return json({ error: "the world did not answer" }, 503);
      const draft = parseDraft(raw, kind, world.me_name);
      if (!draft) {
        return json(
          { error: kind === "org" ? "couldn't place that newsroom — try its name" : "the world answered in a shape I couldn't use" },
          502,
        );
      }
      draft.handle = await uniqueHandle(env, world, kind, draft.handle);
      return json({ ok: true, profile: draft });
    }

    case "create_persona": {
      const persona = parsePersona(p, newId("custom"), true, world.me_name);
      if (!persona) return json({ error: "name and handle required" }, 400);
      const ins = await env.DB.prepare(
        "INSERT OR IGNORE INTO personas (world_id, id, name, handle, bio, occupation, location, voice, gradient, joined_year, follower_boost, model, custom) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)",
      )
        .bind(
          world.id,
          persona.id,
          persona.name,
          persona.handle,
          persona.bio,
          persona.occupation,
          persona.location,
          persona.voice,
          persona.gradient,
          persona.joinedYear,
          persona.followerBoost,
          persona.custom,
        )
        .run();
      if ((ins.meta.changes ?? 0) === 0) return json({ ok: true, id: persona.id, duplicate: true });
      // "Who they are to you" lives in relations against the human ("you"), so
      // every prompt knows this is not a stranger — the wife stays the wife.
      if (persona.relation) {
        await env.DB.prepare("INSERT INTO relations (world_id, a, b, label) VALUES (?, ?, 'you', ?)")
          .bind(world.id, persona.id, persona.relation)
          .run();
      }
      // The app's own semantics: a fresh persona follows you, not the reverse.
      await setFollow(env, world, persona.id, { following: 0, followed_by: 1 });
      await pushActivity(
        env,
        world.id,
        persona.id,
        persona.name,
        persona.relation ? " joined the world and followed you" : " just moved in and followed you",
        "Follow",
      );
      await push(env, world.id, "persona", {
        id: persona.id,
        name: persona.name,
        handle: persona.handle,
        bio: persona.bio,
        occupation: persona.occupation,
        location: persona.location,
        voice: persona.voice,
        gradient: JSON.parse(persona.gradient),
        joinedYear: persona.joinedYear,
        followerBoost: persona.followerBoost,
        relation: persona.relation,
      });
      await schedule(env, world.id, t + randInt(30_000, 120_000), "write_post", { personaId: persona.id });
      return json({ ok: true, id: persona.id });
    }

    case "create_org": {
      const preset = str(p["preset"]).trim();
      if (preset) {
        const result = await addPresetOrg(env, world, preset);
        if (!result) return json({ error: "unknown newsroom" }, 400);
        return json({ ok: true, id: result.org.id, duplicate: !result.created, profile: orgPayload(result.org) });
      }
      const custom = parseCustomOrg(p);
      if (!custom) return json({ error: "a name, a handle and a public feed URL are required" }, 400);
      const result = await addCustomOrg(env, world, custom);
      if ("error" in result) return json({ error: result.error }, 400);
      return json({ ok: true, id: result.org.id, duplicate: !result.created, profile: orgPayload(result.org) });
    }

    default:
      return json({ error: "unknown action" }, 400);
  }
}

// ---- presence ------------------------------------------------------------

export async function handlePresence(env: Env, body: Record<string, unknown>): Promise<Response> {
  const world = await authenticate(env, body);
  if (!world) return json({ error: "unauthorized" }, 401);
  const personaId = str(body["personaId"]).trim();
  const presence = await getPresence(env, world, {
    personaId: personaId || undefined,
    force: body["force"] === true,
    debug: body["debug"] === true,
  });
  return json(presence);
}

// ---- world notes ---------------------------------------------------------

export async function handleNotes(env: Env, body: Record<string, unknown>): Promise<Response> {
  const world = await authenticate(env, body);
  if (!world) return json({ error: "unauthorized" }, 401);
  const notes = await getNotes(env, world.id, 50);
  return json({ notes, t: now() });
}

// ---- export / delete -----------------------------------------------------

const TABLES = [
  "personas",
  "relations",
  "posts",
  "replies",
  "stories",
  "dms",
  "comment_asks",
  "follows",
  "activity",
  "context",
  "orgs",
  "news_seen",
] as const;

export async function handleExport(env: Env, body: Record<string, unknown>): Promise<Response> {
  const world = await authenticate(env, body);
  if (!world) return json({ error: "unauthorized" }, 401);
  await ensureNewsSchema(env);
  const out: Record<string, unknown> = {};
  for (const tb of TABLES) {
    const res = await env.DB.prepare(`SELECT * FROM ${tb} WHERE world_id = ?`).bind(world.id).all();
    out[tb] = res.results;
  }
  // `full` adds the internals — the scheduler queue and the sync feed — for
  // diagnosing a world that is quiet when it should not be.
  if (body["full"] === true) {
    for (const tb of ["jobs", "outbox", "commands"] as const) {
      const res = await env.DB.prepare(`SELECT * FROM ${tb} WHERE world_id = ?`).bind(world.id).all();
      out[tb] = res.results;
    }
  }
  const { api_key: _apiKey, ...worldSafe } = world;
  return json({ world: worldSafe, ...out });
}

export async function handleDelete(env: Env, body: Record<string, unknown>): Promise<Response> {
  const world = await authenticate(env, body);
  if (!world) return json({ error: "unauthorized" }, 401);
  if (str(body["confirm"]) !== "delete") return json({ error: "confirm required" }, 400);
  await ensureNewsSchema(env);
  const stmts = [
    ...TABLES.map((tb) => env.DB.prepare(`DELETE FROM ${tb} WHERE world_id = ?`).bind(world.id)),
    env.DB.prepare("DELETE FROM outbox WHERE world_id = ?").bind(world.id),
    env.DB.prepare("DELETE FROM jobs WHERE world_id = ?").bind(world.id),
    env.DB.prepare("DELETE FROM commands WHERE world_id = ?").bind(world.id),
    env.DB.prepare("DELETE FROM world_keys WHERE world_id = ?").bind(world.id),
    env.DB.prepare("DELETE FROM worlds WHERE id = ?").bind(world.id),
  ];
  await env.DB.batch(stmts);
  return json({ ok: true });
}
