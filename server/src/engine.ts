/**
 * The engine client — one write, retried politely, or silence.
 * Every call counts against the world's daily budget; when the budget is
 * gone, personas are simply quiet. Never canned.
 */

import type { Env, WorldRow } from "./types";
import { sanitize } from "./prompts";
import { now, sleep } from "./util";

const RETRY_DELAYS_MS = [8_000, 20_000]; // between attempts 1→2 and 2→3
const MAX_ATTEMPTS = 3;

export interface Turn {
  role: "user" | "assistant";
  content: string;
}

/**
 * One chat completion for [world]. Returns the sanitized line, or null.
 * [ambient] writes (the world living on its own) stop at 80% of the daily
 * budget so anything the human drives always has the last calls.
 */
export async function writeChat(
  env: Env,
  world: WorldRow,
  system: string,
  turns: Turn[],
  maxTokens: number,
  personaModel: string | null,
  ambient = false,
): Promise<string | null> {
  if (world.calls_today >= world.daily_budget) return null;
  if (ambient && world.calls_today >= Math.floor(world.daily_budget * 0.8)) return null;

  const endpoint = (world.endpoint?.trim() || env.DEFAULT_ENDPOINT).replace(/\/+$/, "");
  const model = personaModel?.trim() || world.model?.trim() || env.DEFAULT_MODEL;
  if (!endpoint || !model) return null;
  const url = endpoint.endsWith("/chat/completions") ? endpoint : `${endpoint}/chat/completions`;

  await env.DB.prepare("UPDATE worlds SET calls_today = calls_today + 1 WHERE id = ?")
    .bind(world.id)
    .run();
  world.calls_today += 1;

  const body = JSON.stringify({
    model,
    messages: [{ role: "system", content: system }, ...turns],
    max_tokens: maxTokens,
    temperature: 0.9,
    stream: false,
  });
  const headers: Record<string, string> = { "content-type": "application/json" };
  const key = world.api_key?.trim();
  if (key) headers.authorization = `Bearer ${key}`;

  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    if (attempt > 0) await sleep(RETRY_DELAYS_MS[attempt - 1]);
    try {
      const res = await fetch(url, { method: "POST", headers, body });
      const text = await res.text();
      if (!res.ok) {
        // Throttles and server blips pass; client errors won't.
        if (res.status === 429 || res.status >= 500) continue;
        console.log("engine http", res.status, text.slice(0, 140));
        return null;
      }
      const data = JSON.parse(text) as { choices?: { message?: { content?: string } }[] };
      const content = data.choices?.[0]?.message?.content?.trim();
      if (!content || content === "null") return null;
      return sanitize(content);
    } catch (e) {
      // A dropped connection is worth another try, same as the app.
      console.log("engine net", String(e).slice(0, 140));
    }
  }
  console.log("engine gave up for world", world.id, "at", now());
  return null;
}
