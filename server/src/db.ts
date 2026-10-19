/** D1 access helpers + the outbox / activity / scheduling primitives. */

import type {
  ActivityRow,
  Env,
  FollowRow,
  NoteRow,
  PersonaRow,
  RelationRow,
  WorldRow,
} from "./types";
import { dayKey, newId, now } from "./util";

// ---- reads ---------------------------------------------------------------

export async function getWorld(env: Env, id: string): Promise<WorldRow | null> {
  return await env.DB.prepare("SELECT * FROM worlds WHERE id = ?").bind(id).first<WorldRow>();
}

export async function getPersonas(env: Env, worldId: string): Promise<PersonaRow[]> {
  const res = await env.DB.prepare("SELECT * FROM personas WHERE world_id = ?").bind(worldId).all<PersonaRow>();
  return res.results;
}

export async function getRelations(env: Env, worldId: string): Promise<RelationRow[]> {
  const res = await env.DB.prepare("SELECT * FROM relations WHERE world_id = ?").bind(worldId).all<RelationRow>();
  return res.results;
}

export async function getFollows(env: Env, worldId: string): Promise<Map<string, FollowRow>> {
  const res = await env.DB.prepare("SELECT * FROM follows WHERE world_id = ?").bind(worldId).all<FollowRow>();
  return new Map(res.results.map((f) => [f.persona_id, f]));
}

export async function getPersona(env: Env, worldId: string, id: string): Promise<PersonaRow | null> {
  return await env.DB.prepare("SELECT * FROM personas WHERE world_id = ? AND id = ?")
    .bind(worldId, id)
    .first<PersonaRow>();
}

/** The human's own notes about the world, newest first. */
export async function getNotes(env: Env, worldId: string, limit = 6): Promise<NoteRow[]> {
  const res = await env.DB.prepare(
    "SELECT id, text, at FROM context WHERE world_id = ? AND source = 'you' AND active = 1 ORDER BY at DESC LIMIT ?",
  )
    .bind(worldId, Math.max(1, Math.min(100, limit)))
    .all<NoteRow>();
  return res.results;
}

// ---- the outbox ----------------------------------------------------------

/** Everything the app learns about goes through here. Returns the new seq. */
export async function push(env: Env, worldId: string, kind: string, payload: unknown): Promise<number> {
  const res = await env.DB.prepare(
    "INSERT INTO outbox (world_id, kind, payload, created_at) VALUES (?, ?, ?, ?)",
  )
    .bind(worldId, kind, JSON.stringify(payload), now())
    .run();
  return Number(res.meta.last_row_id ?? 0);
}

export async function pushActivity(
  env: Env,
  worldId: string,
  personaId: string,
  boldPart: string,
  rest: string,
  kind: string,
  createdAt: number = now(),
): Promise<void> {
  const row: ActivityRow = {
    world_id: worldId,
    id: newId("a"),
    persona_id: personaId,
    bold_part: boldPart,
    rest,
    kind,
    created_at: createdAt,
  };
  await env.DB.prepare(
    "INSERT INTO activity (world_id, id, persona_id, bold_part, rest, kind, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
  )
    .bind(row.world_id, row.id, row.persona_id, row.bold_part, row.rest, row.kind, row.created_at)
    .run();
  await push(env, worldId, "signal", {
    id: row.id,
    personaId: row.persona_id,
    boldPart: row.bold_part,
    rest: row.rest,
    kind: row.kind,
    createdAt: row.created_at,
  });
}

// ---- scheduling ----------------------------------------------------------

export async function schedule(
  env: Env,
  worldId: string,
  at: number,
  kind: string,
  payload: Record<string, unknown> = {},
): Promise<void> {
  await env.DB.prepare(
    "INSERT INTO jobs (id, world_id, run_at, kind, payload, done, created_at) VALUES (?, ?, ?, ?, ?, 0, ?)",
  )
    .bind(newId("job"), worldId, at, kind, JSON.stringify(payload), now())
    .run();
}

// ---- day / budget --------------------------------------------------------

/** Roll the world's call counter when its local day changes. */
export async function resetDayIfNeeded(env: Env, world: WorldRow): Promise<void> {
  const key = dayKey(now(), world.utc_offset);
  if (world.calls_day === key) return;
  world.calls_day = key;
  world.calls_today = 0;
  await env.DB.prepare("UPDATE worlds SET calls_day = ?, calls_today = 0 WHERE id = ?")
    .bind(key, world.id)
    .run();
}

// ---- follows -------------------------------------------------------------

export async function setFollow(
  env: Env,
  world: WorldRow,
  personaId: string,
  patch: { following?: number; followed_by?: number },
): Promise<FollowRow> {
  await env.DB.prepare(
    "INSERT OR IGNORE INTO follows (world_id, persona_id, following, followed_by) VALUES (?, ?, 0, 0)",
  )
    .bind(world.id, personaId)
    .run();
  if (patch.following !== undefined) {
    await env.DB.prepare("UPDATE follows SET following = ? WHERE world_id = ? AND persona_id = ?")
      .bind(patch.following, world.id, personaId)
      .run();
  }
  if (patch.followed_by !== undefined) {
    await env.DB.prepare("UPDATE follows SET followed_by = ? WHERE world_id = ? AND persona_id = ?")
      .bind(patch.followed_by, world.id, personaId)
      .run();
  }
  const row = await env.DB.prepare("SELECT * FROM follows WHERE world_id = ? AND persona_id = ?")
    .bind(world.id, personaId)
    .first<FollowRow>();
  const final = row ?? { world_id: world.id, persona_id: personaId, following: 0, followed_by: 0 };
  await push(env, world.id, "follow", {
    personaId,
    following: final.following === 1,
    followedBy: final.followed_by === 1,
  });
  return final;
}

// ---- content -------------------------------------------------------------

export async function insertPost(
  env: Env,
  world: WorldRow,
  authorId: string,
  text: string,
  idParam?: string,
  extras?: Record<string, unknown>,
): Promise<string> {
  const t = now();
  const id = idParam ?? newId("post");
  await env.DB.prepare(
    "INSERT INTO posts (world_id, id, author_id, text, created_at, likes, replies, liked_by_me) VALUES (?, ?, ?, ?, ?, 0, 0, 0)",
  )
    .bind(world.id, id, authorId, text, t)
    .run();
  await push(env, world.id, "post", {
    id,
    authorId,
    text,
    createdAt: t,
    likes: 0,
    replies: 0,
    likedByMe: false,
    ...(extras ?? {}),
  });
  return id;
}

export async function insertReply(
  env: Env,
  world: WorldRow,
  postId: string,
  authorId: string,
  text: string,
  idParam?: string,
): Promise<string> {
  const t = now();
  const id = idParam ?? newId("reply");
  await env.DB.prepare(
    "INSERT INTO replies (world_id, id, post_id, author_id, text, created_at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(world.id, id, postId, authorId, text, t)
    .run();
  await env.DB.prepare("UPDATE posts SET replies = replies + 1 WHERE world_id = ? AND id = ?")
    .bind(world.id, postId)
    .run();
  await push(env, world.id, "reply", { id, postId, authorId, text, createdAt: t });
  await pushPostUpdate(env, world.id, postId);
  return id;
}

export async function insertStory(
  env: Env,
  world: WorldRow,
  personaId: string,
  text: string,
): Promise<void> {
  const t = now();
  const id = newId("story");
  await env.DB.prepare(
    "INSERT INTO stories (world_id, id, persona_id, text, created_at) VALUES (?, ?, ?, ?, ?)",
  )
    .bind(world.id, id, personaId, text, t)
    .run();
  await push(env, world.id, "story", { id, personaId, text, createdAt: t });
}

export async function insertDm(
  env: Env,
  world: WorldRow,
  personaId: string,
  fromMe: boolean,
  text: string,
  idParam?: string,
): Promise<string> {
  const t = now();
  const id = idParam ?? newId("dm");
  await env.DB.prepare(
    "INSERT INTO dms (world_id, id, persona_id, from_me, text, at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(world.id, id, personaId, fromMe ? 1 : 0, text, t)
    .run();
  await push(env, world.id, "dm", { id, personaId, fromMe, text, at: t });
  return id;
}

/** Push the post's current counters so clients stay exact. */
export async function pushPostUpdate(env: Env, worldId: string, postId: string): Promise<void> {
  const post = await env.DB.prepare("SELECT * FROM posts WHERE world_id = ? AND id = ?")
    .bind(worldId, postId)
    .first<{ id: string; author_id: string; text: string; created_at: number; likes: number; replies: number; liked_by_me: number }>();
  if (!post) return;
  await push(env, worldId, "post", {
    id: post.id,
    authorId: post.author_id,
    text: post.text,
    createdAt: post.created_at,
    likes: post.likes,
    replies: post.replies,
    likedByMe: post.liked_by_me === 1,
  });
}
