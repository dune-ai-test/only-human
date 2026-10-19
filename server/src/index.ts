/** The world server's front door: a fetch router and the every-minute heartbeat. */

import { handleAct, handleCreate, handleDelete, handleExport, handleNotes, handlePresence, handleSync } from "./api";
import { runDueJobs } from "./director";
import type { Env } from "./types";
import { json } from "./util";
import { htmlResponse, landingPage, windowPage } from "./web";

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
      // Also proves the D1 binding + schema are in place: the setup check.
      let db = false;
      let tables: string[] = [];
      try {
        await env.DB.prepare("SELECT 1").first();
        db = true;
        const rows = await env.DB.prepare(
          "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
        ).all<{ name: string }>();
        tables = (rows.results ?? []).map((r) => r.name);
      } catch {
        db = false;
      }
      return json({
        ok: true,
        db,
        tables,
        vars: { endpoint: Boolean(env.DEFAULT_ENDPOINT), model: Boolean(env.DEFAULT_MODEL) },
        t: Date.now(),
      });
    }
    // The site: a quiet landing page, and the console window into a world.
    if (req.method === "GET" || req.method === "HEAD") {
      if (url.pathname === "/") return htmlResponse(landingPage());
      if (url.pathname === "/w" || url.pathname === "/w/") return htmlResponse(windowPage());
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
        case "/v1/presence":
          return await handlePresence(env, body);
        case "/v1/notes":
          return await handleNotes(env, body);
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
