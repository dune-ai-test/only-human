/**
 * The director — rules decide when and what kind; the model only writes
 * the words. Every job runs here: world ticks, warm-up writes, replies,
 * comment asks. Failures are silence, never filler.
 */

import {
  getFollows,
  getNotes,
  getPersona,
  getPersonas,
  getRelations,
  insertDm,
  insertPost,
  insertReply,
  insertStory,
  push,
  pushActivity,
  pushPostUpdate,
  resetDayIfNeeded,
  schedule,
  setFollow,
} from "./db";
import { writeChat } from "./engine";
import {
  commentAsk,
  commenterReplyAsk,
  dmDayAsk,
  dmOpenAsk,
  dmPrompt,
  mentionAsk,
  notesPrompt,
  personaPrompt,
  postAsk,
  replyBackAsk,
  storyAsk,
  trimStory,
  yourPostReplyAsk,
} from "./prompts";
import type { Env, FollowRow, JobRow, PersonaRow, PostRow, ReplyRow, WorldRow } from "./types";
import { newId, nextMorning, now, pick, quietNow, randInt } from "./util";

// ---- persona choice ------------------------------------------------------

/** People who follow the human write more; the same face never repeats twice. */
function pickPersona(
  personas: PersonaRow[],
  follows: Map<string, FollowRow>,
  used: Set<string>,
): PersonaRow | null {
  const pool = personas.filter((p) => !used.has(p.id));
  if (!pool.length) return null;
  const weighted: PersonaRow[] = [];
  for (const p of pool) {
    const f = follows.get(p.id);
    let w = f?.followed_by ? 4 : 1;
    if (p.custom === 1) w = Math.max(1, w - 1);
    for (let i = 0; i < w; i++) weighted.push(p);
  }
  return pick(weighted);
}

async function personaSystem(env: Env, world: WorldRow, persona: PersonaRow): Promise<string> {
  const [personas, relations, notes] = await Promise.all([
    getPersonas(env, world.id),
    getRelations(env, world.id),
    getNotes(env, world.id, 6),
  ]);
  const nameOf = (id: string) =>
    id === "you" ? world.me_name : personas.find((p) => p.id === id)?.name ?? id;
  return personaPrompt(world, persona, relations, nameOf) + notesPrompt(world, notes);
}

async function myPosts(env: Env, worldId: string, limit: number): Promise<PostRow[]> {
  const res = await env.DB.prepare(
    "SELECT * FROM posts WHERE world_id = ? AND author_id = 'you' ORDER BY created_at DESC LIMIT ?",
  )
    .bind(worldId, limit)
    .all<PostRow>();
  return res.results;
}

// ---- the tick ------------------------------------------------------------

export async function runTick(env: Env, world: WorldRow): Promise<void> {
  await resetDayIfNeeded(env, world);
  const t = now();

  const scheduleNext = async (at: number) => {
    world.next_tick_at = at;
    await env.DB.prepare("UPDATE worlds SET next_tick_at = ? WHERE id = ?").bind(at, world.id).run();
    await schedule(env, world.id, at, "tick");
  };

  // An abandoned world (no sync in 30 days) stops ticking — it sleeps.
  if (world.last_contact_at > 0 && t - world.last_contact_at > 30 * 86_400_000) return;

  if (quietNow(t, world.utc_offset)) {
    await scheduleNext(nextMorning(t, world.utc_offset));
    return;
  }
  // Ambient life fades at 80% of the budget; the human's calls keep the rest.
  if (world.calls_today >= Math.floor(world.daily_budget * 0.8)) {
    await scheduleNext(t + randInt(20, 40) * 60_000);
    return;
  }

  const personas = await getPersonas(env, world.id);
  if (personas.length) {
    const follows = await getFollows(env, world.id);
    const roll = Math.random();
    // Most beats are quiet, but the world must not disappear. If nothing
    // visible has happened for a while, this beat is forced to act.
    let events = roll < 0.55 ? 0 : roll < 0.9 ? 1 : 2;
    const last = await env.DB.prepare(
      "SELECT MAX(created_at) AS at FROM outbox WHERE world_id = ? AND kind IN ('post','story','dm','reply','signal')",
    )
      .bind(world.id)
      .first<{ at: number | null }>();
    const silentFor = t - (last?.at ?? 0);
    if (silentFor > 30 * 60_000) events = Math.max(events, 2);
    else if (silentFor > 15 * 60_000) events = Math.max(events, 1);
    const used = new Set<string>();
    for (let i = 0; i < events; i++) {
      const persona = pickPersona(personas, follows, used);
      if (!persona) break;
      used.add(persona.id);
      await runEvent(env, world, persona, follows);
    }
  }
  await scheduleNext(t + randInt(5, 14) * 60_000);
}

async function runEvent(
  env: Env,
  world: WorldRow,
  persona: PersonaRow,
  follows: Map<string, FollowRow>,
): Promise<void> {
  const system = await personaSystem(env, world, persona);
  const roll = Math.random() * 100;
  const mine = await myPosts(env, world.id, 5);

  if (roll < 45) {
    const text = await writeChat(env, world, system, [{ role: "user", content: postAsk }], 140, persona.model, true);
    if (text) await insertPost(env, world, persona.id, text);
    return;
  }
  if (roll < 60) {
    const text = await writeChat(env, world, system, [{ role: "user", content: storyAsk }], 100, persona.model, true);
    if (text) await insertStory(env, world, persona.id, trimStory(text));
    return;
  }
  if (roll < 72) {
    const text = await writeChat(env, world, system, [{ role: "user", content: dmDayAsk(world) }], 130, persona.model, true);
    if (text) {
      await insertDm(env, world, persona.id, false, text);
      await pushActivity(env, world.id, persona.id, persona.name, " sent you a message", "Message");
    }
    return;
  }
  if (roll < 80 && mine.length) {
    const target = pick(mine);
    if (!target) return;
    const text = await writeChat(env, world, system, [{ role: "user", content: yourPostReplyAsk(world, target.text) }], 140, persona.model, true);
    if (text) {
      await insertReply(env, world, target.id, persona.id, text);
      await pushActivity(env, world.id, persona.id, persona.name, ` replied: \u201C${text}\u201D`, "Reply");
    }
    return;
  }
  if (roll < 88 && mine.length) {
    const target = pick(mine);
    if (!target) return;
    await env.DB.prepare("UPDATE posts SET likes = likes + 1 WHERE world_id = ? AND id = ?")
      .bind(world.id, target.id)
      .run();
    await pushPostUpdate(env, world.id, target.id);
    await pushActivity(env, world.id, persona.id, persona.name, " liked your post", "Like");
    return;
  }
  if (roll < 94) {
    const mineFollow = follows.get(persona.id);
    if (mineFollow?.followed_by === 1) {
      // Already following — do the next most human thing instead.
      if (mine.length) {
        const target = pick(mine);
        if (!target) return;
        await env.DB.prepare("UPDATE posts SET likes = likes + 1 WHERE world_id = ? AND id = ?")
          .bind(world.id, target.id)
          .run();
        await pushPostUpdate(env, world.id, target.id);
        await pushActivity(env, world.id, persona.id, persona.name, " liked your post", "Like");
      }
      return;
    }
    await setFollow(env, world, persona.id, { followed_by: 1 });
    await pushActivity(env, world.id, persona.id, persona.name, " started following you", "Follow");
    return;
  }
  const text = await writeChat(env, world, system, [{ role: "user", content: mentionAsk(world) }], 140, persona.model, true);
  if (text) {
    await insertPost(env, world, persona.id, text);
    await pushActivity(env, world.id, persona.id, persona.name, " mentioned you in a post", "Mention");
  }
}

// ---- jobs ----------------------------------------------------------------

export async function runDueJobs(env: Env): Promise<void> {
  const t = now();
  const due = await env.DB.prepare(
    "SELECT * FROM jobs WHERE done = 0 AND run_at <= ? ORDER BY run_at LIMIT 60",
  )
    .bind(t)
    .all<JobRow>();

  for (const job of due.results) {
    const marked = await env.DB.prepare("UPDATE jobs SET done = 1 WHERE id = ? AND done = 0")
      .bind(job.id)
      .run();
    if ((marked.meta.changes ?? 0) === 0) continue;
    const world = await env.DB.prepare("SELECT * FROM worlds WHERE id = ?")
      .bind(job.world_id)
      .first<WorldRow>();
    if (!world) continue;
    try {
      await runJob(env, world, job);
    } catch (e) {
      console.log("job failed", job.kind, String(e).slice(0, 200));
    }
  }

  // Housekeeping: finished jobs and day-old command ids.
  await env.DB.prepare("DELETE FROM jobs WHERE done = 1 AND created_at < ?").bind(t - 7 * 86_400_000).run();
  await env.DB.prepare("DELETE FROM commands WHERE created_at < ?").bind(t - 86_400_000).run();
}

async function runJob(env: Env, world: WorldRow, job: JobRow): Promise<void> {
  const payload = JSON.parse(job.payload || "{}") as Record<string, string>;
  switch (job.kind) {
    case "tick":
      return runTick(env, world);
    case "write_post": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const system = await personaSystem(env, world, persona);
      const text = await writeChat(env, world, system, [{ role: "user", content: postAsk }], 140, persona.model);
      if (text) await insertPost(env, world, persona.id, text);
      return;
    }
    case "write_story": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const system = await personaSystem(env, world, persona);
      const text = await writeChat(env, world, system, [{ role: "user", content: storyAsk }], 100, persona.model);
      if (text) await insertStory(env, world, persona.id, trimStory(text));
      return;
    }
    case "dm_open": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const system = dmPrompt(await personaSystem(env, world, persona));
      const text = await writeChat(env, world, system, [{ role: "user", content: dmOpenAsk(world) }], 120, persona.model);
      if (text) {
        await insertDm(env, world, persona.id, false, text);
        await pushActivity(env, world.id, persona.id, persona.name, " sent you a message", "Message");
      }
      return;
    }
    case "dm_reply": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const history = await env.DB.prepare(
        "SELECT * FROM dms WHERE world_id = ? AND persona_id = ? ORDER BY at DESC LIMIT 12",
      )
        .bind(world.id, persona.id)
        .all<{ from_me: number; text: string }>();
      const turns = history.results
        .reverse()
        .map((d) => ({ role: (d.from_me === 1 ? "user" : "assistant") as "user" | "assistant", content: d.text }));
      if (!turns.length || turns[turns.length - 1].role !== "user") return;
      const system = dmPrompt(await personaSystem(env, world, persona));
      const text = await writeChat(env, world, system, turns, 130, persona.model);
      if (text) {
        await insertDm(env, world, persona.id, false, text);
        await pushActivity(env, world.id, persona.id, persona.name, " sent you a message", "Message");
      }
      return;
    }
    case "reply_back": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const post = await env.DB.prepare("SELECT * FROM posts WHERE world_id = ? AND id = ?")
        .bind(world.id, payload.postId)
        .first<PostRow>();
      if (!post) return;
      const mineReply = await env.DB.prepare(
        "SELECT * FROM replies WHERE world_id = ? AND post_id = ? AND author_id = 'you' ORDER BY created_at DESC LIMIT 1",
      )
        .bind(world.id, payload.postId)
        .first<ReplyRow>();
      if (!mineReply) return;
      const system = await personaSystem(env, world, persona);
      let text: string | null = null;
      if (payload.mode === "commenter") {
        const theirComment = await env.DB.prepare(
          "SELECT * FROM replies WHERE world_id = ? AND post_id = ? AND author_id = ? ORDER BY created_at DESC LIMIT 1",
        )
          .bind(world.id, payload.postId, persona.id)
          .first<ReplyRow>();
        if (!theirComment) return;
        text = await writeChat(
          env,
          world,
          system,
          [{ role: "user", content: commenterReplyAsk(world, post.text, theirComment.text, mineReply.text) }],
          140,
          persona.model,
        );
      } else {
        text = await writeChat(
          env,
          world,
          system,
          [{ role: "user", content: replyBackAsk(world, post.text, mineReply.text) }],
          140,
          persona.model,
        );
      }
      if (text) {
        await insertReply(env, world, post.id, persona.id, text);
        await pushActivity(env, world.id, persona.id, persona.name, ` replied: \u201C${text}\u201D`, "Reply");
      }
      return;
    }
    case "ask": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const post = await env.DB.prepare("SELECT * FROM posts WHERE world_id = ? AND id = ?")
        .bind(world.id, payload.postId)
        .first<PostRow>();
      if (!post || post.author_id !== "you") return;
      const existing = await env.DB.prepare(
        "SELECT id FROM comment_asks WHERE world_id = ? AND post_id = ? AND persona_id = ? AND state = 'pending'",
      )
        .bind(world.id, payload.postId, persona.id)
        .first<{ id: string }>();
      if (existing) return;
      const id = newId("ask");
      const at = now();
      await env.DB.prepare(
        "INSERT INTO comment_asks (world_id, id, post_id, persona_id, at, state) VALUES (?, ?, ?, ?, ?, 'pending')",
      )
        .bind(world.id, id, post.id, persona.id, at)
        .run();
      await push(env, world.id, "ask", { id, postId: post.id, personaId: persona.id, at, state: "pending" });
      return;
    }
    case "comment_write": {
      const ask = await env.DB.prepare("SELECT * FROM comment_asks WHERE world_id = ? AND id = ?")
        .bind(world.id, payload.askId)
        .first<{ id: string; post_id: string; persona_id: string; state: string }>();
      if (!ask || ask.state !== "approved") return;
      const persona = await getPersona(env, world.id, ask.persona_id);
      const post = await env.DB.prepare("SELECT * FROM posts WHERE world_id = ? AND id = ?")
        .bind(world.id, ask.post_id)
        .first<PostRow>();
      if (!persona || !post) return;
      const system = await personaSystem(env, world, persona);
      const text = await writeChat(env, world, system, [{ role: "user", content: commentAsk(world, post.text) }], 140, persona.model);
      if (!text) {
        // Silence: the ask returns to pending; you can ask again.
        await env.DB.prepare("UPDATE comment_asks SET state = 'pending' WHERE world_id = ? AND id = ?")
          .bind(world.id, ask.id)
          .run();
        await push(env, world.id, "ask", { id: ask.id, postId: ask.post_id, personaId: ask.persona_id, at: now(), state: "pending" });
        return;
      }
      await insertReply(env, world, post.id, persona.id, text);
      await pushActivity(env, world.id, persona.id, persona.name, ` commented: \u201C${text}\u201D`, "Reply");
      await env.DB.prepare("UPDATE comment_asks SET state = 'posted' WHERE world_id = ? AND id = ?")
        .bind(world.id, ask.id)
        .run();
      await push(env, world.id, "ask", { id: ask.id, postId: ask.post_id, personaId: ask.persona_id, at: now(), state: "posted" });
      return;
    }
    case "follow_back": {
      const persona = await getPersona(env, world.id, payload.personaId);
      if (!persona) return;
      const follows = await getFollows(env, world.id);
      if (follows.get(persona.id)?.followed_by === 1) return;
      await setFollow(env, world, persona.id, { followed_by: 1 });
      await pushActivity(env, world.id, persona.id, persona.name, " started following you", "Follow");
      return;
    }
    default:
      return;
  }
}
