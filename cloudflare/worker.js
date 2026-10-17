/**
 * Only Human — LLM proxy worker.
 *
 * Keeps your API key off the phone. The app points its "Endpoint" setting at
 * this worker; the worker forwards OpenAI-compatible calls upstream with the
 * real key attached.
 *
 * Deploy (see ./README.md for the full walkthrough):
 *   wrangler secret put OPENAI_API_KEY
 *   wrangler secret put UPSTREAM_BASE        # e.g. https://api.openai.com/v1
 *   wrangler secret put APP_TOKEN             # optional shared secret
 *   wrangler deploy
 */

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "POST, OPTIONS",
  "access-control-allow-headers": "content-type, authorization, x-only-human-token",
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS });
    }
    if (request.method === "GET") {
      return new Response("Only Human proxy is alive.", { headers: CORS });
    }
    if (request.method !== "POST") {
      return new Response("POST only", { status: 405, headers: CORS });
    }

    // Optional shared secret so randoms can't use your worker as a free LLM.
    if (env.APP_TOKEN && request.headers.get("x-only-human-token") !== env.APP_TOKEN) {
      return new Response("unauthorized", { status: 401, headers: CORS });
    }

    const upstreamBase = (env.UPSTREAM_BASE || "https://api.openai.com/v1").replace(/\/+$/, "");
    const url = new URL(request.url);
    const target = upstreamBase + url.pathname + url.search;

    const headers = new Headers(request.headers);
    headers.delete("host");
    headers.delete("x-only-human-token");
    if (env.OPENAI_API_KEY) {
      headers.set("authorization", `Bearer ${env.OPENAI_API_KEY}`);
    }

    const upstream = await fetch(target, {
      method: "POST",
      headers,
      body: await request.arrayBuffer(),
    });

    const out = new Headers(upstream.headers);
    for (const [k, v] of Object.entries(CORS)) out.set(k, v);
    return new Response(upstream.body, { status: upstream.status, headers: out });
  },
};
