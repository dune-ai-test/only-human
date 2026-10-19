/**
 * Where everyone is, right now.
 *
 * Presence is written by the model, not chosen from a list of canned moods:
 * one batch call places the people whose state has gone stale. The result is
 * cached in the same `context` table the world already uses, so this needs no
 * schema change and survives a deploy. When the model is silent, the previous
 * state stays — with its real age — and nothing is invented.
 */

import { getPersonas } from "./db";
import { writeRaw } from "./engine";
import type { Env, PersonaRow, WorldRow } from "./types";
import { now, quietNow } from "./util";

const PLACES = [
  "home",
  "work",
  "cafe",
  "library",
  "park",
  "street",
  "transit",
  "shop",
  "outdoors",
  "asleep",
  "elsewhere",
] as const;

type Place = (typeof PLACES)[number];

const TTL = 15 * 60_000;
const RETRY = 2 * 60_000;

interface Cached {
  place: Place;
  doing: string;
  at: number;
}

interface Last {
  kind: string;
  text: string;
  at: number;
}

export interface PresencePerson {
  id: string;
  place: Place;
  doing: string;
  at: number;
  stale: boolean;
}

export interface PresenceResponse {
  at: number;
  people: PresencePerson[];
  budget: boolean;
  raw?: string | null;
}

export interface PresenceOptions {
  personaId?: string;
  force?: boolean;
  debug?: boolean;
}

function clip(text: string, n: number): string {
  const flat = String(text || "")
    .replace(/\s+/g, " ")
    .trim();
  return flat.length <= n ? flat : flat.slice(0, n - 1).trimEnd() + "\u2026";
}

function agoText(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000));
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.max(1, Math.round(s / 60))}m ago`;
  if (s < 86400) return `${Math.round(s / 3600)}h ago`;
  return `${Math.round(s / 86400)}d ago`;
}

function coercePlace(raw: unknown, doing: string): Place {
  const p = String(raw ?? "")
    .toLowerCase()
    .trim();
  if ((PLACES as readonly string[]).includes(p)) return p as Place;

  // The model usually obeys the list; when it doesn't, the words still tell
  // us enough to group the row without inventing anything.
  const t = doing.toLowerCase();
  if (/sleep|asleep|in bed|bedroom/.test(t)) return "asleep";
  if (/library|bookshelf|archive|reading room/.test(t)) return "library";
  if (/caf\u00e9|cafe|coffee|espresso|tea shop/.test(t)) return "cafe";
  if (/park|bench|garden|allotment|trees/.test(t)) return "park";
  if (/bus|train|subway|tram|platform|commut/.test(t)) return "transit";
  if (/shop|market|grocer|store|bakery/.test(t)) return "shop";
  if (/walk|street|road|pavement|sidewalk|corner|bridge/.test(t)) return "street";
  if (/office|studio|desk|work|shift|venue/.test(t)) return "work";
  if (/home|flat|apartment|sofa|kitchen|laundry/.test(t)) return "home";
  if (/outside|outdoors|harbour|harbor|canal|river|hill|shore/.test(t)) return "outdoors";
  return "elsewhere";
}

function parsePresence(raw: string, targets: string[]): { id: string; place: Place; doing: string }[] {
  const allowed = new Set(targets);
  let text = raw.trim();
  text = text.replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();

  let parsed: unknown = null;
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start >= 0 && end > start) {
    try {
      parsed = JSON.parse(text.slice(start, end + 1));
    } catch {
      parsed = null;
    }
  }
  if (!Array.isArray(parsed)) {
    const os = text.indexOf("{");
    const oe = text.lastIndexOf("}");
    if (os >= 0 && oe > os) {
      try {
        const obj = JSON.parse(text.slice(os, oe + 1)) as Record<string, unknown>;
        if (Array.isArray(obj["people"])) parsed = obj["people"];
      } catch {
        parsed = null;
      }
    }
  }
  if (!Array.isArray(parsed)) return [];

  const out: { id: string; place: Place; doing: string }[] = [];
  const seen = new Set<string>();
  // If the model malforms exactly one id (it once wrote "work" where a
  // persona id belonged), the array still has one slot per person in the
  // order we asked for. Recover that slot, but only when the counts match —
  // otherwise a shifted list would put one person's day on another.
  const positional = parsed.length === targets.length;
  for (let index = 0; index < parsed.length; index++) {
    const item = parsed[index];
    if (!item || typeof item !== "object") continue;
    const o = item as Record<string, unknown>;
    let id = typeof o["id"] === "string" ? o["id"].trim() : "";
    if (!allowed.has(id) || seen.has(id)) {
      const expected = targets[index];
      if (!positional || !expected || seen.has(expected) || allowed.has(id)) continue;
      id = expected;
    }
    const doing = String(o["doing"] ?? o["activity"] ?? "")
      .replace(/\r?\n/g, " ")
      .replace(/^["'\u201C\u201D ]+|["'\u201C\u201D ]+$/g, "")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 160);
    if (doing.length < 2) continue;
    out.push({ id, place: coercePlace(o["place"], doing), doing });
    seen.add(id);
  }
  return out;
}

function buildSystem(world: WorldRow, t: number): string {
  const local = new Date(t + world.utc_offset * 60_000);
  const days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
  const h = local.getUTCHours();
  const m = local.getUTCMinutes();
  const time = `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")}${h < 12 ? "am" : "pm"}`;
  const quiet = quietNow(t, world.utc_offset);
  const phase = h < 5 ? "night" : h < 12 ? "morning" : h < 17 ? "afternoon" : h < 22 ? "evening" : "night";

  return [
    "You keep the quiet, ordinary lives of a small circle of friends on a private social app.",
    `It is ${days[local.getUTCDay()]}, ${time}. They live in different cities but keep one shared clock.`,
    quiet
      ? "This is the middle of the night in their shared time. Most of them are asleep; one or two night owls may still be up. Keep it quiet."
      : `It is ${phase}. Some are out, some are working, some are home.`,
    "Write where each person is and what they are doing at this minute. Be specific: a room, a street, a task, a small interruption.",
    "Write 5 to 16 words for doing, in complete, natural phrases. Keep the small words - never telegram style.",
    "Not everyone is doing something interesting. People wait, commute, work, eat, queue, and fall asleep on the sofa.",
    "Keep it physically plausible. Avoid brand names, measurements, and technical parts; if a hobby needs a specific tool to make sense, choose a simpler action.",
    "Keep each person in or near their own city. Do not invent drama, weather events, or meetings with other people unless their recent post implies it.",
    "Never mention the app or the content they make. Do not use the words post, posting, story, feed, app, online, phone, or being watched. Describe their life, not their timeline.",
    "Do not use the person's name in doing.",
    `place must be exactly one of: ${PLACES.join(", ")}.`,
    "Return a JSON array only, with one object for every person listed, no prose and no code fences:",
    '[{"id":"maya","place":"cafe","doing":"..."}]',
  ].join("\n");
}

async function buildUser(
  env: Env,
  world: WorldRow,
  targets: PersonaRow[],
  cached: Map<string, Cached>,
  t: number,
): Promise<string> {
  const posts = await env.DB.prepare(
    "SELECT author_id AS pid, text, created_at FROM posts WHERE world_id = ? ORDER BY created_at DESC LIMIT 160",
  )
    .bind(world.id)
    .all<{ pid: string; text: string; created_at: number }>();
  const stories = await env.DB.prepare(
    "SELECT persona_id AS pid, text, created_at FROM stories WHERE world_id = ? ORDER BY created_at DESC LIMIT 80",
  )
    .bind(world.id)
    .all<{ pid: string; text: string; created_at: number }>();

  const last = new Map<string, Last>();
  const note = (pid: string, text: string, at: number, kind: string) => {
    if (!pid || !text) return;
    const prev = last.get(pid);
    if (!prev || at > prev.at) last.set(pid, { kind, text, at });
  };
  for (const p of posts.results) note(p.pid, p.text, p.created_at, "post");
  for (const s of stories.results) note(s.pid, s.text, s.created_at, "story");

  const lines: string[] = [];
  lines.push(`Place these ${targets.length} people.`);
  for (const p of targets) {
    const city = p.location ? `, ${p.location}` : "";
    const job = p.occupation ? `, ${p.occupation}` : "";
    lines.push(`- id: ${p.id}`);
    lines.push(`  ${p.name}${job}${city}`);
    if (p.voice) lines.push(`  voice: ${clip(p.voice, 110)}`);
    if (p.bio) lines.push(`  bio: ${clip(p.bio, 130)}`);
    const recent = last.get(p.id);
    if (recent) {
      lines.push(`  last ${recent.kind}, ${agoText(t - recent.at)}: \u201C${clip(recent.text, 130)}\u201D`);
    }
    const prev = cached.get(p.id);
    if (prev) {
      lines.push(`  a moment ago: ${prev.place} \u2014 ${clip(prev.doing, 90)} (${agoText(t - prev.at)})`);
    }
  }
  lines.push("");
  lines.push("Return the array now, one object per id, in the same order. JSON only.");
  return lines.join("\n");
}

async function generate(
  env: Env,
  world: WorldRow,
  targets: PersonaRow[],
  cached: Map<string, Cached>,
  t: number,
): Promise<{ entries: { id: string; place: Place; doing: string }[]; raw: string | null }> {
  const system = buildSystem(world, t);
  const user = await buildUser(env, world, targets, cached, t);
  const raw = await writeRaw(env, world, system, [{ role: "user", content: user }], 1000, null, false);
  if (!raw) return { entries: [], raw: null };
  return { entries: parsePresence(raw, targets.map((p) => p.id)), raw };
}

async function readCache(env: Env, worldId: string): Promise<Map<string, Cached>> {
  const res = await env.DB.prepare(
    "SELECT id, text, at FROM context WHERE world_id = ? AND source = 'presence' AND id <> 'presence:meta'",
  )
    .bind(worldId)
    .all<{ id: string; text: string; at: number }>();
  const map = new Map<string, Cached>();
  for (const row of res.results) {
    const id = row.id.startsWith("presence:") ? row.id.slice("presence:".length) : row.id;
    try {
      const data = JSON.parse(row.text) as { place?: unknown; doing?: unknown };
      const doing = typeof data.doing === "string" ? data.doing.trim() : "";
      if (!doing) continue;
      map.set(id, { place: coercePlace(data.place, doing), doing, at: row.at });
    } catch {
      // A corrupt row is not presence. Ignore it.
    }
  }
  return map;
}

async function readMeta(env: Env, worldId: string): Promise<number> {
  const row = await env.DB.prepare("SELECT at FROM context WHERE world_id = ? AND id = 'presence:meta'")
    .bind(worldId)
    .first<{ at: number }>();
  return row?.at ?? 0;
}

async function savePresence(
  env: Env,
  worldId: string,
  entries: { id: string; place: Place; doing: string }[],
  t: number,
): Promise<void> {
  if (!entries.length) return;
  await env.DB.batch(
    entries.map((e) =>
      env.DB.prepare(
        "INSERT INTO context (world_id, id, source, text, at, active) VALUES (?, ?, 'presence', ?, ?, 1) " +
          "ON CONFLICT(world_id, id) DO UPDATE SET text = excluded.text, at = excluded.at, active = 1",
      ).bind(worldId, `presence:${e.id}`, JSON.stringify({ place: e.place, doing: e.doing }), t),
    ),
  );
}

async function saveMeta(env: Env, worldId: string, t: number): Promise<void> {
  await env.DB.prepare(
    "INSERT INTO context (world_id, id, source, text, at, active) VALUES (?, 'presence:meta', 'presence', '', ?, 1) " +
      "ON CONFLICT(world_id, id) DO UPDATE SET text = '', at = excluded.at, active = 1",
  )
    .bind(worldId, t)
    .run();
}

export async function getPresence(
  env: Env,
  world: WorldRow,
  opts: PresenceOptions = {},
): Promise<PresenceResponse> {
  const t = now();
  const all = (await getPersonas(env, world.id)).filter((p) => p.id !== "you");
  const targets = opts.personaId ? all.filter((p) => p.id === opts.personaId) : all;
  let cached = await readCache(env, world.id);
  let raw: string | null = null;

  if (targets.length) {
    const needs = opts.force
      ? targets
      : targets.filter((p) => {
          const c = cached.get(p.id);
          return !c || t - c.at > TTL;
        });
    const canCall = world.calls_today < world.daily_budget;
    const metaAt = await readMeta(env, world.id);
    const guarded = !opts.force && t - metaAt < RETRY;

    if (needs.length && canCall && !guarded) {
      // The default cast is one batch. Larger custom casts are split so the
      // model can actually finish every object instead of truncating the tail.
      const CHUNK = 16;
      for (let i = 0; i < needs.length; i += CHUNK) {
        const slice = needs.slice(i, i + CHUNK);
        const result = await generate(env, world, slice, cached, t);
        if (result.raw) raw = (raw ? raw + "\n" : "") + result.raw;
        if (result.entries.length) {
          await savePresence(env, world.id, result.entries, t);
          const next = new Map(cached);
          for (const e of result.entries) next.set(e.id, { place: e.place, doing: e.doing, at: t });
          cached = next;
        }
        if (result.raw === null) break;
      }
      // Even a failed write backs off for a couple of minutes; the world
      // should not spend the budget retrying in a loop.
      await saveMeta(env, world.id, t);
    }
  }

  const people: PresencePerson[] = [];
  for (const p of targets) {
    const c = cached.get(p.id);
    if (!c) continue;
    people.push({ id: p.id, place: c.place, doing: c.doing, at: c.at, stale: t - c.at > TTL });
  }

  const out: PresenceResponse = {
    at: t,
    people,
    budget: world.calls_today < world.daily_budget,
  };
  if (opts.debug) out.raw = raw ? raw.slice(0, 1200) : null;
  return out;
}
