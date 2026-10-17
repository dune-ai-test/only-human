/** The world server's front door: a fetch router and the every-minute heartbeat. */

import { handleAct, handleCreate, handleDelete, handleExport, handleSync } from "./api";
import { runDueJobs } from "./director";
import type { Env } from "./types";
import { json } from "./util";

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "content-type",
  "access-control-allow-methods": "GET, POST, OPTIONS",
};

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);

    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (req.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, t: Date.now() });
    }
    if (req.method !== "POST") return json({ error: "not found" }, 404);

    let body: Record<string, unknown>;
    try {
      const parsed: unknown = await req.json();
      body = (parsed ?? {}) as Record<string, unknown>;
    } catch {
      return json({ error: "bad json" }, 400);
    }

    try {
      switch (url.pathname) {
        case "/v1/worlds":
          return await handleCreate(env, body);
        case "/v1/sync":
          return await handleSync(env, body);
        case "/v1/act":
          return await handleAct(env, body);
        case "/v1/export":
          return await handleExport(env, body);
        case "/v1/delete":
          return await handleDelete(env, body);
        default:
          return json({ error: "not found" }, 404);
      }
    } catch (e) {
      console.log("api error", url.pathname, String(e).slice(0, 300));
      return json({ error: "server error" }, 500);
    }
  },

  async scheduled(_event: ScheduledController, env: Env, _ctx: ExecutionContext): Promise<void> {
    await runDueJobs(env);
  },
} satisfies ExportedHandler<Env>;
