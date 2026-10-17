/** The prompts — a faithful port of the app's Engine.kt / World.kt wording. */

import type { PersonaRow, RelationRow, WorldRow } from "./types";

type Personaish = Pick<PersonaRow, "id" | "name" | "handle" | "bio" | "occupation" | "location" | "voice">;

/** The system prompt that makes [persona] sound like themselves. */
export function personaPrompt(
  world: WorldRow,
  persona: Personaish,
  relations: RelationRow[],
  nameOf: (id: string) => string,
): string {
  const ties = relations
    .filter((r) => r.a === persona.id || r.b === persona.id)
    .map((r) => `- ${nameOf(r.a === persona.id ? r.b : r.a)}: ${r.label}`)
    .join("\n");

  let out = "";
  out += `You are ${persona.name}, @${persona.handle}, on a tiny social app called Only Human.\n`;
  out += `About you: ${persona.bio}\n`;
  if (persona.occupation) {
    out += `What you do: ${persona.occupation}`;
    if (persona.location) out += `, living in ${persona.location}`;
    out += ` — it colors what you notice and what you post about.\n`;
  }
  out += `Your writing style — this defines you: ${persona.voice}.\n`;
  out += `The person you're writing to is ${world.me_name}, @${world.me_handle} — the only human on the app, and your new neighbor. Everyone else here is part of your small circle of friends.\n`;
  if (ties) out += `People you know:\n${ties}\n`;
  out += `Rules, always: write like a real person — never mention AI, models, assistants or prompts; no hashtags; lowercase is natural; no emoji unless your style calls for it; one or two short sentences; never echo the message back; never explain the joke.`;
  return out;
}

/** [personaPrompt] plus a nudge that this is a private conversation. */
export function dmPrompt(system: string): string {
  return system + "\nThis is a private DM conversation. DMs are more relaxed than posts: ask things, reference your day, keep it short.";
}

// ---- what they actually write -------------------------------------------

export const postAsk =
  "Write your latest post for the feed — something small from your day, " +
  "in your voice. One or two short sentences, plain text only, no hashtags.";

export const storyAsk =
  "Write your story for the next 24 hours — the one thing you're doing or " +
  "noticing right now, this minute. One or two very short lines, present tense, " +
  "plain text only, no hashtags.";

export function commentAsk(world: WorldRow, postText: string): string {
  return (
    `${world.me_name} (@${world.me_handle}) posted: \u201C${postText}\u201D\n` +
    "Write a short comment on their post, in your voice. One or two sentences, plain text only, no quotes."
  );
}

export function replyBackAsk(world: WorldRow, postText: string, myReply: string): string {
  return (
    `Context — your post: \u201C${postText}\u201D\n` +
    `${world.me_name} (@${world.me_handle}) replied to it: \u201C${myReply}\u201D\n` +
    "Write your reply back. One or two short sentences, plain text only, no quotes."
  );
}

export function commenterReplyAsk(
  world: WorldRow,
  postText: string,
  theirComment: string,
  myReply: string,
): string {
  return (
    `Context — ${world.me_name}'s post: \u201C${postText}\u201D\n` +
    `You commented: \u201C${theirComment}\u201D\n` +
    `${world.me_name} (@${world.me_handle}) replied to you: \u201C${myReply}\u201D\n` +
    "Write your reply back to them. One or two short sentences, plain text only, no quotes."
  );
}

export function yourPostReplyAsk(world: WorldRow, postText: string): string {
  return (
    `Your friend ${world.me_name} (@${world.me_handle}) posted: \u201C${postText}\u201D\n` +
    "Write a short reply to it, in your voice. One or two sentences, plain text only."
  );
}

export function mentionAsk(world: WorldRow): string {
  return (
    `Something in your day made you think of your neighbor ${world.me_name} (@${world.me_handle}). ` +
    "Write a short post mentioning them by @handle. One or two sentences, plain text only."
  );
}

export function dmOpenAsk(world: WorldRow): string {
  return (
    `Write the first DM you'd send to your new neighbor ${world.me_name} — something personal, ` +
    "in your voice, that gives them an easy reason to reply. One or two lines."
  );
}

export function dmDayAsk(world: WorldRow): string {
  return (
    `Send ${world.me_name} a short message about your day — a thought, a question, ` +
    "something only you would send. One or two lines, plain text only."
  );
}

// ---- output hygiene ------------------------------------------------------

/** One line, no quotes, no newlines, capped — the way they'd actually send it. */
export function sanitize(text: string): string {
  const flat = text
    .replace(/\r/g, " ")
    .replace(/\n/g, " ")
    .trim()
    .replace(/^["\u201C\u201D ]+|["\u201C\u201D ]+$/g, "");
  return flat.length <= 280 ? flat : flat.slice(0, 277).trimEnd() + "\u2026";
}

/** Stories live on one screen: keep the first sentences, never mid-word. */
export function trimStory(raw: string): string {
  const text = raw.trim();
  if (text.length <= 220) return text;
  const head = text.slice(0, 220);
  const cut = Math.max(head.lastIndexOf("."), head.lastIndexOf("!"), head.lastIndexOf("?"));
  if (cut >= 60) return head.slice(0, cut + 1);
  const space = head.lastIndexOf(" ");
  return space > 0 ? head.slice(0, space).replace(/[,;:]+$/, "").trimEnd() : head;
}
