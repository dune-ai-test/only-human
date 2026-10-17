# Only Human — Cloudflare proxy (optional)

Lets you point the app at any OpenAI-compatible endpoint **without shipping the API key in the APK** — the key lives in Cloudflare secrets, and the worker attaches it.

## One-time setup

```bash
npm install -g wrangler
wrangler login                      # opens the browser, log in with your Cloudflare account
cd cloudflare
wrangler secret put OPENAI_API_KEY  # paste your key (OpenAI, OpenRouter, etc.)
wrangler secret put UPSTREAM_BASE   # e.g. https://api.openai.com/v1  (or http://<your-lan>:11434/v1 for Ollama)
wrangler secret put APP_TOKEN       # optional shared secret; if set, the app must send the same token
wrangler deploy
```

`wrangler deploy` prints the URL, e.g. `https://only-human-proxy.<you>.workers.dev`.

## In the app

Settings → AI → Endpoint: paste that URL (when the AI screen lands in the next nightly).
The worker passes through paths like `/chat/completions`, so the app talks to it exactly like it would talk to OpenAI.

## Test it

```bash
curl https://only-human-proxy.<you>.workers.dev/chat/completions \
  -H "content-type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"say hi in one word"}]}'
```

If you set `APP_TOKEN`, add `-H "x-only-human-token: <token>"` to that call, and set the same value in the app's settings.
