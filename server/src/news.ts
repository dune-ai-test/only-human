/**
 * Newsrooms — real organizations, square profiles, real headlines.
 *
 * The model never writes these. Each newsroom has a public RSS feed; the
 * heartbeat picks the newest unseen headline across the world's newsrooms and
 * posts it, at most once per cooldown. Fetch or parse failures are silence:
 * there is no invented news, ever.
 */

import { insertPost, push } from "./db";
import type { Env, OrgRow, WorldRow } from "./types";
import { newId, now } from "./util";

export interface OrgPreset {
  id: string;
  name: string;
  handle: string;
  kind: string;
  bio: string;
  city: string;
  color: string;
  monogram: string;
  feed: string;
}

export const ORG_PRESETS: Record<string, OrgPreset> = {
  "org-guardian": {
    id: "org-guardian",
    name: "The Guardian",
    handle: "guardian",
    kind: "news",
    bio: "World news, reported and edited in London. Headlines land here as they happen.",
    city: "London",
    color: "#052962",
    monogram: "G",
    feed: "https://www.theguardian.com/world/rss",
  },
  "org-bbc": {
    id: "org-bbc",
    name: "BBC News",
    handle: "bbcnews",
    kind: "news",
    bio: "Breaking news and world stories from BBC News.",
    city: "London",
    color: "#B80000",
    monogram: "BBC",
    feed: "https://feeds.bbci.co.uk/news/world/rss.xml",
  },
  "org-npr": {
    id: "org-npr",
    name: "NPR",
    handle: "npr",
    kind: "news",
    bio: "News and culture from across America, as it happens.",
    city: "Washington",
    color: "#111111",
    monogram: "NPR",
    feed: "https://feeds.npr.org/1004/rss.xml",
  },
  "org-aljazeera": {
    id: "org-aljazeera",
    name: "Al Jazeera",
    handle: "aljazeera",
    kind: "news",
    bio: "Stories and news from across the world.",
    city: "Doha",
    color: "#B8860B",
    monogram: "AJ",
    feed: "https://www.aljazeera.com/xml/rss/all.xml",
  },
};

const DEFAULT_ORG_IDS = ["org-guardian", "org-bbc"];
const NEWS_COOLDOWN_MS = 90 * 60_000;
const FETCH_EVERY_MS = 10 * 60_000;
const MAX_AGE_MS = 12 * 60 * 60_000;
const MAX_TITLE = 240;

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS orgs (
    world_id TEXT NOT NULL,
    id TEXT NOT NULL,
    name TEXT NOT NULL,
    handle TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'news',
    bio TEXT NOT NULL DEFAULT '',
    city TEXT NOT NULL DEFAULT '',
    color TEXT NOT NULL DEFAULT '#1D1D1F',
    monogram TEXT NOT NULL DEFAULT '',
    feed_url TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    last_fetch_at INTEGER NOT NULL DEFAULT 0,
    last_post_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (world_id, id)
  )`,
  `CREATE TABLE IF NOT EXISTS news_seen (
    world_id TEXT NOT NULL,
    org_id TEXT NOT NULL,
    url TEXT NOT NULL,
    seen_at INTEGER NOT NULL,
    PRIMARY KEY (world_id, org_id, url)
  )`,
];

let schemaReady = false;

export async function ensureNewsSchema(env: Env): Promise<void> {
  if (schemaReady) return;
  await env.DB.batch(SCHEMA.map((sql) => env.DB.prepare(sql)));
  schemaReady = true;
}

export async function getOrgs(env: Env, worldId: string): Promise<OrgRow[]> {
  await ensureNewsSchema(env);
  const res = await env.DB.prepare(
    "SELECT * FROM orgs WHERE world_id = ? AND active = 1 ORDER BY created_at ASC",
  )
    .bind(worldId)
    .all<OrgRow>();
  return res.results;
}

export async function getOrg(env: Env, worldId: string, id: string): Promise<OrgRow | null> {
  await ensureNewsSchema(env);
  return await env.DB.prepare("SELECT * FROM orgs WHERE world_id = ? AND id = ? AND active = 1")
    .bind(worldId, id)
    .first<OrgRow>();
}

function orgPayload(org: OrgRow): Record<string, unknown> {
  return {
    id: org.id,
    name: org.name,
    handle: org.handle,
    bio: org.bio,
    occupation: "newsroom",
    location: org.city,
    voice: "newsroom",
    gradient: [org.color, org.color],
    joinedYear: "",
    followerBoost: 0,
    kind: "org",
    monogram: org.monogram,
    color: org.color,
  };
}

export { orgPayload };

/** Insert a real newsroom from a feed the human supplied. */
export interface CustomOrgInput {
  name: string;
  handle: string;
  city: string;
  bio: string;
  feedUrl: string;
  monogram: string;
  color: string;
}

const CUSTOM_ORG_COLORS = [
  "#0A84FF",
  "#30B0C7",
  "#34C759",
  "#FF9F0A",
  "#FF375F",
  "#BF5AF2",
  "#5E5CE6",
  "#8E8E93",
];

function styleHash(value: string): number {
  let n = 0;
  for (let i = 0; i < value.length; i++) n = (n * 31 + value.charCodeAt(i)) >>> 0;
  return n;
}

function initials(name: string): string {
  const words = name.replace(/[^\p{L}\p{N} ]/gu, " ").split(/\s+/).filter(Boolean);
  if (!words.length) return "N";
  if (words.length === 1) return words[0].slice(0, 3).toUpperCase();
  return words.slice(0, 4).map((w) => w[0]).join("").toUpperCase();
}

function colorFor(name: string): string {
  return CUSTOM_ORG_COLORS[styleHash(name) % CUSTOM_ORG_COLORS.length];
}

export async function addCustomOrg(
  env: Env,
  world: WorldRow,
  input: CustomOrgInput,
): Promise<{ org: OrgRow; created: boolean } | { error: string }> {
  await ensureNewsSchema(env);

  const sameFeed = await env.DB.prepare(
    "SELECT * FROM orgs WHERE world_id = ? AND feed_url = ? AND active = 1 LIMIT 1",
  )
    .bind(world.id, input.feedUrl)
    .first<OrgRow>();
  if (sameFeed) return { org: sameFeed, created: false };

  const sameHandle = await env.DB.prepare(
    "SELECT 1 FROM orgs WHERE world_id = ? AND lower(handle) = lower(?) AND active = 1 LIMIT 1",
  )
    .bind(world.id, input.handle)
    .first();
  if (sameHandle) return { error: "that handle is already here" };

  const personHandle = await env.DB.prepare(
    "SELECT 1 FROM personas WHERE world_id = ? AND lower(handle) = lower(?) LIMIT 1",
  )
    .bind(world.id, input.handle)
    .first();
  if (personHandle) return { error: "that handle is already here" };

  const items = await readFeed(input.feedUrl).catch(() => []);
  if (!items.length) {
    return { error: "that feed did not answer with any headlines" };
  }

  const t = now();
  const id = newId("org");
  const monogram = input.monogram.replace(/[^A-Za-z0-9]/g, "").toUpperCase().slice(0, 4) || initials(input.name);
  const color = /^#[0-9a-f]{6}$/i.test(input.color) ? input.color : colorFor(input.name);
  const ins = await env.DB.prepare(
    "INSERT OR IGNORE INTO orgs (world_id, id, name, handle, kind, bio, city, color, monogram, feed_url, active, created_at, last_fetch_at, last_post_at) VALUES (?, ?, ?, ?, 'news', ?, ?, ?, ?, ?, 1, ?, 0, 0)",
  )
    .bind(
      world.id,
      id,
      input.name,
      input.handle,
      input.bio,
      input.city,
      color,
      monogram,
      input.feedUrl,
      t,
    )
    .run();
  const org = await getOrg(env, world.id, id);
  if (!org) return { error: "the newsroom could not be saved" };
  const created = (ins.meta.changes ?? 0) > 0;
  if (created) {
    await push(env, world.id, "persona", orgPayload(org));
    // Let the next heartbeat look at the new feed immediately.
    await env.DB.prepare("DELETE FROM context WHERE world_id = ? AND id = 'news:meta'")
      .bind(world.id)
      .run();
  }
  return { org, created };
}

/** Add one of the known newsrooms. Returns null for an unknown preset. */
export async function addPresetOrg(
  env: Env,
  world: WorldRow,
  presetId: string,
): Promise<{ org: OrgRow; created: boolean } | null> {
  await ensureNewsSchema(env);
  const preset = ORG_PRESETS[presetId];
  if (!preset) return null;
  const existing = await getOrg(env, world.id, preset.id);
  if (existing) return { org: existing, created: false };
  const t = now();
  const ins = await env.DB.prepare(
    "INSERT OR IGNORE INTO orgs (world_id, id, name, handle, kind, bio, city, color, monogram, feed_url, active, created_at, last_fetch_at, last_post_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 0, 0)",
  )
    .bind(
      world.id,
      preset.id,
      preset.name,
      preset.handle,
      preset.kind,
      preset.bio,
      preset.city,
      preset.color,
      preset.monogram,
      preset.feed,
      t,
    )
    .run();
  const org = await getOrg(env, world.id, preset.id);
  if (!org) return null;
  const created = (ins.meta.changes ?? 0) > 0;
  if (created) {
    await push(env, world.id, "persona", orgPayload(org));
    // A newsroom just arrived; let the next heartbeat look at its feed even
    // if another headline was posted a few minutes ago.
    await env.DB.prepare("DELETE FROM context WHERE world_id = ? AND id = 'news:meta'")
      .bind(world.id)
      .run();
  }
  return { org, created };
}

/** A world without newsrooms gets two to start with. */
export async function ensureSeedOrgs(env: Env, world: WorldRow): Promise<void> {
  await ensureNewsSchema(env);
  const row = await env.DB.prepare("SELECT COUNT(*) AS n FROM orgs WHERE world_id = ? AND active = 1")
    .bind(world.id)
    .first<{ n: number }>();
  if ((row?.n ?? 0) > 0) return;
  for (const id of DEFAULT_ORG_IDS) await addPresetOrg(env, world, id);
}

// ---- RSS / Atom ----------------------------------------------------------

interface FeedItem {
  title: string;
  url: string;
  publishedAt: number;
}

const ENTITIES: Record<string, string> = {
  amp: "&",
  quot: '"',
  apos: "'",
  lt: "<",
  gt: ">",
  nbsp: " ",
  hellip: "…",
  mdash: "—",
  ndash: "–",
  rsquo: "’",
  lsquo: "‘",
  rdquo: "”",
  ldquo: "“",
};

function decodeEntities(input: string): string {
  return input
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (whole, key: string) => {
      if (key[0] === "#") {
        const hex = key[1] === "x" || key[1] === "X";
        const code = Number.parseInt(key.slice(hex ? 2 : 1), hex ? 16 : 10);
        return Number.isFinite(code) && code >= 0 && code <= 0x10ffff ? String.fromCodePoint(code) : whole;
      }
      return ENTITIES[key.toLowerCase()] ?? whole;
    });
}

function cleanText(input: string, max: number): string {
  return decodeEntities(input)
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function tag(block: string, name: string): string {
  const m = block.match(new RegExp(`<${name}[^>]*>([\\s\\S]*?)</${name}>`, "i"));
  return m ? m[1] : "";
}

function httpUrl(value: string): string {
  const url = decodeEntities(value).trim();
  return /^https?:\/\//i.test(url) ? url : "";
}

/** Tolerant enough for RSS 2.0 and Atom; old or broken items are dropped. */
export function parseFeed(xml: string): FeedItem[] {
  let blocks = [...xml.matchAll(/<item\b[\s\S]*?<\/item>/gi)].map((m) => m[0]);
  if (!blocks.length) {
    blocks = [...xml.matchAll(/<entry\b[\s\S]*?<\/entry>/gi)].map((m) => m[0]);
  }
  const items: FeedItem[] = [];
  for (const block of blocks.slice(0, 20)) {
    const title = cleanText(tag(block, "title"), MAX_TITLE);
    if (!title) continue;
    let url = httpUrl(tag(block, "link"));
    if (!url) {
      const href = block.match(/<link\b[^>]*href=["']([^"']+)["']/i);
      if (href) url = httpUrl(href[1]);
    }
    if (!url) continue;
    const dateRaw =
      tag(block, "pubDate") || tag(block, "published") || tag(block, "updated") || tag(block, "dc:date");
    const parsed = Date.parse(decodeEntities(dateRaw).trim());
    items.push({ title, url, publishedAt: Number.isFinite(parsed) ? parsed : now() });
  }
  return items.sort((a, b) => b.publishedAt - a.publishedAt);
}

async function readFeed(feedUrl: string): Promise<FeedItem[]> {
  if (!feedUrl) return [];
  const init: RequestInit & { cf?: unknown } = {
    headers: {
      accept: "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
      "user-agent": "OnlyHumanWorld/1.0 (+https://only-human.duneaitest.workers.dev)",
    },
    cf: { cacheTtl: 600, cacheEverything: true },
  };
  const res = await fetch(feedUrl, init);
  if (!res.ok) return [];
  return parseFeed(await res.text());
}

async function fetchFeed(org: OrgRow): Promise<FeedItem[]> {
  if (!org.feed_url) return [];
  return readFeed(org.feed_url);
}

// ---- posting -------------------------------------------------------------

export async function fetchNewsForWorld(env: Env, world: WorldRow): Promise<void> {
  await ensureNewsSchema(env);
  const orgs = await getOrgs(env, world.id);
  if (!orgs.length) return;
  const t = now();

  const metaRow = await env.DB.prepare(
    "SELECT text FROM context WHERE world_id = ? AND id = 'news:meta'",
  )
    .bind(world.id)
    .first<{ text: string }>();
  let lastPostAt = 0;
  try {
    const parsed = JSON.parse(metaRow?.text ?? "{}") as { lastPostAt?: unknown };
    lastPostAt = Number(parsed.lastPostAt) || 0;
  } catch {
    lastPostAt = 0;
  }
  if (lastPostAt > 0 && t - lastPostAt < NEWS_COOLDOWN_MS) return;

  const candidates: { org: OrgRow; item: FeedItem }[] = [];
  for (const org of orgs) {
    if (!org.feed_url) continue;
    if (t - org.last_fetch_at < FETCH_EVERY_MS) continue;
    let items: FeedItem[] = [];
    try {
      items = await fetchFeed(org);
    } catch {
      items = [];
    }
    await env.DB.prepare("UPDATE orgs SET last_fetch_at = ? WHERE world_id = ? AND id = ?")
      .bind(t, world.id, org.id)
      .run();
    if (!items.length) continue;

    const seenRows = await env.DB.prepare(
      "SELECT url FROM news_seen WHERE world_id = ? AND org_id = ?",
    )
      .bind(world.id, org.id)
      .all<{ url: string }>();
    const seen = new Set(seenRows.results.map((r) => r.url));
    for (const item of items) {
      if (seen.has(item.url)) continue;
      if (t - item.publishedAt > MAX_AGE_MS) continue;
      candidates.push({ org, item });
    }
  }

  if (!candidates.length) return;
  candidates.sort((a, b) => b.item.publishedAt - a.item.publishedAt);
  const pick = candidates[0];
  const title = cleanText(pick.item.title, MAX_TITLE);
  if (!title) return;

  await insertPost(env, world, pick.org.id, title, undefined, {
    url: pick.item.url,
    source: pick.org.name,
    publishedAt: pick.item.publishedAt,
    news: true,
  });
  await env.DB.prepare(
    "INSERT OR IGNORE INTO news_seen (world_id, org_id, url, seen_at) VALUES (?, ?, ?, ?)",
  )
    .bind(world.id, pick.org.id, pick.item.url, t)
    .run();
  await env.DB.prepare("UPDATE orgs SET last_post_at = ? WHERE world_id = ? AND id = ?")
    .bind(t, world.id, pick.org.id)
    .run();
  await env.DB.prepare(
    "INSERT OR REPLACE INTO context (world_id, id, source, text, at, active) VALUES (?, 'news:meta', 'news', ?, ?, 1)",
  )
    .bind(world.id, JSON.stringify({ lastPostAt: t }), t)
    .run();
}

/** Called by the every-minute heartbeat. One headline per world at a time. */
export async function runNews(env: Env): Promise<void> {
  try {
    await ensureNewsSchema(env);
  } catch (e) {
    console.log("news schema", String(e).slice(0, 200));
    return;
  }
  const worlds = await env.DB.prepare("SELECT * FROM worlds").all<WorldRow>();
  for (const world of worlds.results) {
    try {
      await ensureSeedOrgs(env, world);
      await fetchNewsForWorld(env, world);
    } catch (e) {
      console.log("news error", world.id, String(e).slice(0, 200));
    }
  }
}

