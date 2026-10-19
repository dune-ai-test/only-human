/**
 * Group chats — the human and a few of the cast in one room.
 *
 * A group has a name and a topic. Every message that comes from a persona is
 * model-written; the server answers 15–45s after you write, from a member you
 * tagged first, otherwise from one of them at random.
 */

import { push } from "./db";
import type { Env, GroupMsgRow, GroupRow, WorldRow } from "./types";
import { newId, now } from "./util";

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS groups (
    world_id   TEXT NOT NULL,
    id         TEXT NOT NULL,
    name       TEXT NOT NULL,
    topic      TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (world_id, id)
  )`,
  `CREATE TABLE IF NOT EXISTS group_members (
    world_id   TEXT NOT NULL,
    group_id   TEXT NOT NULL,
    persona_id TEXT NOT NULL,
    at         INTEGER NOT NULL,
    PRIMARY KEY (world_id, group_id, persona_id)
  )`,
  `CREATE TABLE IF NOT EXISTS group_msgs (
    world_id  TEXT NOT NULL,
    id        TEXT NOT NULL,
    group_id  TEXT NOT NULL,
    author_id TEXT NOT NULL,
    text      TEXT NOT NULL,
    at        INTEGER NOT NULL,
    PRIMARY KEY (world_id, id)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_group_msgs ON group_msgs(world_id, group_id, at)`,
];

let schemaReady = false;

export async function ensureGroupSchema(env: Env): Promise<void> {
  if (schemaReady) return;
  await env.DB.batch(SCHEMA.map((sql) => env.DB.prepare(sql)));
  schemaReady = true;
}

export async function getGroup(env: Env, worldId: string, id: string): Promise<GroupRow | null> {
  await ensureGroupSchema(env);
  return await env.DB.prepare("SELECT * FROM groups WHERE world_id = ? AND id = ?")
    .bind(worldId, id)
    .first<GroupRow>();
}

export async function getGroupMembers(env: Env, worldId: string, groupId: string): Promise<string[]> {
  await ensureGroupSchema(env);
  const res = await env.DB.prepare(
    "SELECT persona_id FROM group_members WHERE world_id = ? AND group_id = ? ORDER BY at ASC",
  )
    .bind(worldId, groupId)
    .all<{ persona_id: string }>();
  return res.results.map((r) => r.persona_id);
}

/** The last [limit] messages, oldest first — what the model reads. */
export async function getGroupMsgs(env: Env, worldId: string, groupId: string, limit = 16): Promise<GroupMsgRow[]> {
  await ensureGroupSchema(env);
  const res = await env.DB.prepare(
    "SELECT * FROM group_msgs WHERE world_id = ? AND group_id = ? ORDER BY at DESC LIMIT ?",
  )
    .bind(worldId, groupId, Math.max(1, Math.min(40, limit)))
    .all<GroupMsgRow>();
  return res.results.reverse();
}

/** The snapshot clients store — name, topic, and who's in it. */
export async function pushGroup(env: Env, worldId: string, groupId: string): Promise<void> {
  const group = await getGroup(env, worldId, groupId);
  if (!group) return;
  const members = await getGroupMembers(env, worldId, groupId);
  await push(env, worldId, "group", {
    id: group.id,
    name: group.name,
    topic: group.topic,
    members,
    createdAt: group.created_at,
    updatedAt: group.updated_at,
  });
}

export async function createGroup(
  env: Env,
  world: WorldRow,
  name: string,
  topic: string,
  memberIds: string[],
): Promise<GroupRow> {
  await ensureGroupSchema(env);
  const t = now();
  const id = newId("group");
  await env.DB.prepare(
    "INSERT INTO groups (world_id, id, name, topic, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(world.id, id, name, topic, t, t)
    .run();
  for (const personaId of memberIds) {
    await env.DB.prepare(
      "INSERT OR IGNORE INTO group_members (world_id, group_id, persona_id, at) VALUES (?, ?, ?, ?)",
    )
      .bind(world.id, id, personaId, t)
      .run();
  }
  await pushGroup(env, world.id, id);
  return { world_id: world.id, id, name, topic, created_at: t, updated_at: t };
}

export async function insertGroupMsg(
  env: Env,
  world: WorldRow,
  groupId: string,
  authorId: string,
  text: string,
  idParam?: string,
): Promise<string> {
  await ensureGroupSchema(env);
  const t = now();
  const id = idParam ?? newId("gm");
  await env.DB.prepare(
    "INSERT INTO group_msgs (world_id, id, group_id, author_id, text, at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(world.id, id, groupId, authorId, text, t)
    .run();
  await push(env, world.id, "group_msg", { id, groupId, authorId, text, at: t });
  return id;
}
