/**
 * The small web frontend, served by the Worker itself — no assets, no build
 * step, no dependencies. Two pages:
 *
 *   GET /   — the landing: what this server is, in as few words as possible.
 *   GET /w  — the window: paste a world id + recovery key and you're looking
 *             at the world itself — a clock, a timeline of everything that
 *             happens, a composer, messages, and the people. Read *and*
 *             write, through the same authenticated API the app uses. The
 *             key travels only in POST bodies (the deep-link variant keeps
 *             it in the URL fragment, which browsers never send anywhere).
 *
 * The design mirrors the app on purpose: the same palette (iOS system colors,
 * light + dark), the same type scale and tracking, hairline separators, one
 * accent, no images. If it looks like a template, it failed.
 */

// ---- shared ---------------------------------------------------------------

const CSS = `
:root {
  color-scheme: light dark;
  --bg: #FFFFFF;
  --grouped: #F2F2F7;
  --card: #FFFFFF;
  --card2: #F2F2F7;
  --label: #000000;
  --label2: rgba(60, 60, 67, 0.6);
  --label3: rgba(60, 60, 67, 0.3);
  --sep: rgba(60, 60, 67, 0.16);
  --accent: #007AFF;
  --red: #FF3B30;
  --green: #34C759;
  --green-t: #248A3D;
  --orange: #FF9500;
  --press: rgba(0, 0, 0, 0.05);
  --bar: rgba(242, 242, 247, 0.82);
  --bar-plain: rgba(255, 255, 255, 0.8);
  --ring: rgba(0, 122, 255, 0.3);
  --sel: rgba(0, 122, 255, 0.22);
  --seg: rgba(118, 118, 128, 0.12);
  --seg-on: #FFFFFF;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #000000;
    --grouped: #000000;
    --card: #1C1C1E;
    --card2: #2C2C2E;
    --label: #FFFFFF;
    --label2: rgba(235, 235, 245, 0.6);
    --label3: rgba(235, 235, 245, 0.3);
    --sep: rgba(84, 84, 88, 0.35);
    --accent: #0A84FF;
    --red: #FF453A;
    --green: #30D158;
    --green-t: #30D158;
    --orange: #FF9F0A;
    --press: rgba(255, 255, 255, 0.07);
    --bar: rgba(0, 0, 0, 0.72);
    --bar-plain: rgba(0, 0, 0, 0.72);
    --ring: rgba(10, 132, 255, 0.36);
    --sel: rgba(10, 132, 255, 0.3);
    --seg: rgba(118, 118, 128, 0.24);
    --seg-on: #636366;
  }
}
*, *::before, *::after { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; }
body {
  margin: 0;
  background: var(--grouped);
  color: var(--label);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
body.plain { background: var(--bg); }
::selection { background: var(--sel); }
a { color: var(--accent); text-decoration: none; }
button { font: inherit; color: inherit; }
:focus-visible { outline: 3px solid var(--ring); outline-offset: 2px; }

.t-large { font-size: 34px; line-height: 1.2; font-weight: 700; letter-spacing: .37px; margin: 0; }
.t-headline { font-size: 17px; line-height: 22px; font-weight: 600; letter-spacing: -.41px; margin: 0; }
.t-body { font-size: 17px; line-height: 22px; letter-spacing: -.41px; margin: 0; }
.t-sub { font-size: 15px; line-height: 20px; letter-spacing: -.24px; margin: 0; }
.t-foot { font-size: 13px; line-height: 18px; letter-spacing: -.08px; margin: 0; }
.sec { color: var(--label2); }
.mono { font-family: ui-monospace, "SF Mono", SFMono-Regular, Menlo, Consolas, monospace; }
.num { font-variant-numeric: tabular-nums; }

/* ---- landing ------------------------------------------------------------ */

.page { max-width: 560px; margin: 0 auto; padding: 104px 24px 40px; }
.lede { margin: 6px 0 28px; }
.card {
  background: var(--card);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.row { display: flex; gap: 12px; padding: 14px 16px; }
.row + .row { border-top: .5px solid var(--sep); }
.row-main { flex: 1; min-width: 0; }
.row .sub { margin-top: 2px; }
.dot {
  position: relative; flex: none;
  width: 8px; height: 8px; border-radius: 50%;
  margin-top: 7px; background: var(--green);
}
.dot::after {
  content: ""; position: absolute; inset: 0;
  border-radius: 50%; background: var(--green);
  animation: pulse 2.8s cubic-bezier(.2, .6, .3, 1) infinite;
}
@keyframes pulse {
  0% { transform: scale(1); opacity: .4; }
  70% { transform: scale(2.6); opacity: 0; }
  100% { transform: scale(2.6); opacity: 0; }
}
.block .sub { margin-top: 6px; }
.linkrow { align-items: center; }
.linkrow .row-main { display: flex; }
.linkrow svg { display: block; margin-left: auto; color: var(--label3); }
.linkrow:hover { background: var(--press); }
.linkrow, .linkrow:active { -webkit-tap-highlight-color: transparent; }
.foot { text-align: center; margin-top: 28px; color: var(--label3); }
@media (prefers-reduced-motion: no-preference) {
  .page > * { animation: rise .6s cubic-bezier(.2, .6, .2, 1) both; }
  .page > *:nth-child(2) { animation-delay: .05s; }
  .page > *:nth-child(3) { animation-delay: .1s; }
  .page > *:nth-child(4) { animation-delay: .15s; }
}
@keyframes rise { from { opacity: 0; transform: translateY(8px); } }

/* ---- window ------------------------------------------------------------- */

.bar {
  position: sticky; top: 0; z-index: 10;
  display: flex; align-items: center; gap: 8px;
  height: 52px; padding: 0 16px;
  background: var(--bar);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  backdrop-filter: blur(20px) saturate(180%);
  border-bottom: .5px solid var(--sep);
}
body.plain .bar { background: var(--bar-plain); }
.back { display: inline-flex; align-items: center; gap: 5px; margin-left: -4px; }
.back svg { display: block; }
.status { margin-left: auto; }
.window { max-width: 620px; padding: 10px 16px 48px; }
.window .t-large { margin-bottom: 2px; }
.window .lede { margin: 6px 0 20px; }

.formcard { padding: 16px; }
.field { margin-top: 14px; }
.field:first-of-type { margin-top: 0; }
.field label { display: block; margin: 0 2px 6px; }
.field input {
  width: 100%; height: 44px;
  border: 0; border-radius: 10px;
  background: var(--card2); color: var(--label);
  padding: 0 14px;
  font-size: 15px; letter-spacing: .2px;
}
.field input::placeholder { color: var(--label3); }
.field input:focus { outline: none; box-shadow: 0 0 0 3px var(--ring); }
.err { color: var(--red); margin: 12px 2px 0; }
.btn {
  display: block; width: 100%; height: 46px;
  margin-top: 16px; border: 0; border-radius: 999px;
  background: var(--accent); color: #FFFFFF;
  font-size: 17px; font-weight: 600; letter-spacing: -.41px;
  cursor: pointer;
  transition: transform .12s ease, filter .12s ease;
  -webkit-tap-highlight-color: transparent;
}
.btn:hover { filter: brightness(1.07); }
.btn:active { transform: scale(.98); filter: brightness(.96); }
.btn[disabled] { opacity: .45; pointer-events: none; }
.note { margin: 14px 2px 0; }

/* console: clock card */
.statuscard { margin-top: 8px; }
.status-top { display: flex; align-items: flex-start; padding: 16px 16px 10px; }
.clock { font-size: 40px; line-height: 1.05; font-weight: 600; letter-spacing: -.6px; font-variant-numeric: tabular-nums; }
.clock-sub { margin-top: 3px; }
.pill {
  margin-left: auto; flex: none;
  font-size: 13px; font-weight: 600; letter-spacing: -.08px;
  padding: 4px 11px; border-radius: 999px;
  background: var(--seg); color: var(--label2);
}
.pill.awake { background: rgba(52, 199, 89, 0.16); color: var(--green-t); }
.pill.asleep { background: var(--seg); color: var(--label2); }
.stats { display: flex; border-top: .5px solid var(--sep); }
.stat { flex: 1; text-align: center; padding: 9px 4px 10px; min-width: 0; }
.stat + .stat { border-left: .5px solid var(--sep); }
.stat-v { font-size: 17px; line-height: 22px; font-weight: 600; letter-spacing: -.41px; font-variant-numeric: tabular-nums; }
.stat-k { margin-top: 1px; }
.meter { height: 2px; background: var(--seg); }
.meter-fill { height: 100%; width: 0; background: var(--accent); transition: width .4s ease; }
.meter-fill.warn { background: var(--orange); }

/* console: segmented control */
.seg {
  display: flex; gap: 2px;
  background: var(--seg);
  border-radius: 9px; padding: 2px;
  margin: 14px 0 14px;
}
.seg-btn {
  flex: 1; height: 30px;
  border: 0; border-radius: 7px;
  background: transparent; color: var(--label);
  font-size: 13px; font-weight: 600; letter-spacing: -.08px;
  cursor: pointer;
  white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
}
.seg-btn.on { background: var(--seg-on); box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12); }

/* console: composer */
.compose { padding: 12px 16px 12px; }
.ta {
  display: block; width: 100%;
  border: 0; outline: none; resize: none;
  background: transparent; color: var(--label);
  font: inherit; font-size: 17px; line-height: 22px; letter-spacing: -.41px;
  padding: 4px 0; margin: 0;
  max-height: 220px;
}
.ta::placeholder { color: var(--label3); }
.compose-row { display: flex; align-items: center; margin-top: 8px; }
.compose-row .count { margin-left: auto; margin-right: 10px; }
.mini {
  height: 32px; padding: 0 16px;
  border: 0; border-radius: 999px;
  font-size: 15px; font-weight: 600; letter-spacing: -.24px;
  cursor: pointer;
  transition: transform .12s ease, filter .12s ease;
  -webkit-tap-highlight-color: transparent;
}
.mini:active { transform: scale(.97); }
.mini.accent { background: var(--accent); color: #FFFFFF; }
.mini.accent:hover { filter: brightness(1.07); }
.mini.ghost { background: var(--seg); color: var(--label); }
.mini[disabled] { opacity: .4; pointer-events: none; }

/* console: waiting on you */
.waiting { margin-bottom: 14px; }
.waiting h2 { margin: 0 2px 7px; font-size: 13px; font-weight: 600; letter-spacing: -.08px; color: var(--label2); }
.ask { padding: 13px 16px; display: flex; gap: 12px; }
.ask + .ask { border-top: .5px solid var(--sep); }
.ask-main { flex: 1; min-width: 0; }
.ask-q { margin-top: 5px; padding: 7px 11px; border-radius: 10px; background: var(--card2); }
.ask-q .t-sub { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.ask-actions { display: flex; gap: 8px; margin-top: 10px; align-items: center; }
.ask-writing { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.spin {
  width: 12px; height: 12px; flex: none;
  border-radius: 50%; border: 2px solid var(--label3); border-top-color: var(--label2);
  animation: spin 0.9s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spin { animation: none; } }

/* console: timeline */
.time-note { margin-top: 6px; }
.older { text-align: center; margin-top: 18px; }
.event { display: flex; gap: 10px; padding: 7px 0; align-items: flex-start; }
.event-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 7px; flex: none; }
.event-t { flex: 1; min-width: 0; font-size: 15px; line-height: 20px; letter-spacing: -.24px; }
.event-t b { font-weight: 600; }
.event-t .rest { color: var(--label2); }
.event-t .quote { color: var(--label2); }
.event-line { display: flex; gap: 10px; align-items: baseline; }
.event-text { flex: 1; min-width: 0; }
.event-time { flex: none; }
.event .story-line { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

/* console: the log — one line per thing that happened */
.logcard { padding: 0; }
.log-day {
  padding: 13px 16px 5px;
  font-size: 12px; line-height: 16px; font-weight: 600;
  letter-spacing: .02em; text-transform: uppercase;
  color: var(--label3);
}
.logrow { display: flex; gap: 10px; padding: 7px 16px; align-items: flex-start; }
.logrow + .logrow { border-top: .5px solid var(--sep); }
.log-day + .logrow { border-top: 0; }
.log-t {
  flex: none; width: 40px; padding-top: 1px;
  font-size: 12px; line-height: 17px; color: var(--label3);
  font-variant-numeric: tabular-nums;
}
.log-dot { flex: none; width: 7px; height: 7px; border-radius: 50%; margin-top: 5px; }
.log-x { flex: 1; min-width: 0; font-size: 14px; line-height: 19px; letter-spacing: -.15px; }
.log-x b { font-weight: 600; }
.log-x .rest { color: var(--label2); }
.log-x .quote {
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  margin-top: 1px; color: var(--label2);
}

.post { position: relative; display: flex; gap: 12px; padding: 14px 0; }
.post + .post::before,
.post.hair::before {
  content: ""; position: absolute; top: 0; left: 56px; right: 0;
  border-top: .5px solid var(--sep);
}
.av {
  flex: none; width: 44px; height: 44px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #FFFFFF; font-size: 17px; font-weight: 600; letter-spacing: .3px;
  background-image: linear-gradient(180deg, rgba(255, 255, 255, .22), rgba(255, 255, 255, 0) 55%), linear-gradient(135deg, var(--g1), var(--g2));
}
.av.sm { width: 28px; height: 28px; font-size: 11px; letter-spacing: .2px; }
.av.md { width: 36px; height: 36px; font-size: 14px; }
.post-body { flex: 1; min-width: 0; }
.post-body header { display: flex; flex-wrap: wrap; align-items: baseline; column-gap: 6px; }
.name { font-size: 17px; line-height: 22px; font-weight: 600; letter-spacing: -.41px; }
.who { font-size: 15px; line-height: 20px; letter-spacing: -.24px; color: var(--label2); }
.text { margin: 3px 0 0; font-size: 16px; line-height: 22px; letter-spacing: -.32px; white-space: pre-wrap; overflow-wrap: anywhere; }
.counts { margin-top: 6px; font-size: 13px; line-height: 18px; letter-spacing: -.08px; color: var(--label2); }
.actions { display: flex; align-items: center; gap: 18px; margin-top: 7px; }
.act {
  display: inline-flex; align-items: center; gap: 5px;
  border: 0; background: none; padding: 2px 0;
  color: var(--label2); font-size: 14px; letter-spacing: -.15px; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.act svg { display: block; }
.act.liked { color: var(--red); }
.act.liked svg path { fill: currentColor; stroke: currentColor; }
.act.rp { color: var(--label2); }
.act:hover { color: var(--label); }
.act.liked:hover { color: var(--red); }
.replies { margin-top: 8px; display: grid; gap: 9px; }
.reply { display: flex; gap: 9px; }
.reply-main { flex: 1; min-width: 0; }
.reply-head { display: flex; flex-wrap: wrap; align-items: baseline; column-gap: 5px; }
.reply-name { font-size: 14px; line-height: 19px; font-weight: 600; letter-spacing: -.15px; }
.reply-who { font-size: 12px; line-height: 18px; color: var(--label2); }
.reply-text { margin: 0; font-size: 15px; line-height: 20px; letter-spacing: -.24px; white-space: pre-wrap; overflow-wrap: anywhere; }
.reply-compose { margin-top: 9px; padding: 10px 12px; border-radius: 12px; background: var(--card2); }
.reply-compose .ta { font-size: 15px; line-height: 20px; letter-spacing: -.24px; }
.reply-compose .compose-row { margin-top: 6px; }
.opt { opacity: .55; }
.fail-note { display: inline-flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 13px; line-height: 18px; color: var(--red); }
.fail-note button { border: 0; background: none; padding: 0; color: var(--red); font-weight: 600; cursor: pointer; text-decoration: underline; }

/* console: messages */
.dm-row { display: flex; gap: 12px; padding: 12px 0; align-items: center; cursor: pointer; }
.dm-row + .dm-row { border-top: .5px solid var(--sep); }
.dm-main { flex: 1; min-width: 0; }
.dm-preview { margin-top: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dm-time { flex: none; align-self: flex-start; margin-top: 2px; }
.thread-head { display: flex; align-items: center; gap: 10px; margin: 4px 0 6px; }
.thread-name { flex: 1; min-width: 0; }
.bubbles { display: grid; gap: 4px; margin-top: 8px; }
.bubble { max-width: 78%; padding: 8px 13px; border-radius: 18px; width: fit-content; }
.bubble .bt { margin: 0; font-size: 16px; line-height: 21px; letter-spacing: -.32px; white-space: pre-wrap; overflow-wrap: anywhere; }
.bubble.them { background: var(--card2); }
.bubble.me { margin-left: auto; background: var(--accent); color: #FFFFFF; border-bottom-right-radius: 6px; }
.bubble.them { border-bottom-left-radius: 6px; }
.dsep { text-align: center; margin: 12px 0 6px; }
.dm-composer {
  position: sticky; bottom: 14px;
  display: flex; align-items: center; gap: 8px;
  margin-top: 14px;
  background: var(--card);
  border: .5px solid var(--sep);
  border-radius: 999px;
  padding: 6px 6px 6px 16px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.10);
}
.dm-in {
  flex: 1; min-width: 0; height: 32px;
  border: 0; outline: none; background: transparent;
  color: var(--label); font: inherit; font-size: 16px; letter-spacing: -.32px;
}
.dm-in::placeholder { color: var(--label3); }
.sendbtn {
  flex: none; width: 32px; height: 32px;
  border: 0; border-radius: 50%;
  background: var(--accent); color: #FFFFFF;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.sendbtn[disabled] { opacity: .4; pointer-events: none; }
.sendbtn:active { transform: scale(.94); }

/* console: people */
.person { display: flex; gap: 12px; padding: 13px 0; align-items: center; cursor: pointer; }
.person + .person { border-top: .5px solid var(--sep); }
.person-main { flex: 1; min-width: 0; }
.person-bio { margin-top: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.follow-btn {
  flex: none; height: 32px; padding: 0 15px;
  border: 0; border-radius: 999px;
  font-size: 14px; font-weight: 600; letter-spacing: -.15px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.follow-btn.on { background: var(--seg); color: var(--label); }
.follow-btn.off { background: rgba(0, 122, 255, 0.12); color: var(--accent); }
@media (prefers-color-scheme: dark) {
  .follow-btn.off { background: rgba(10, 132, 255, 0.18); }
}

/* console: footer + misc */
.feed-foot { text-align: center; margin-top: 36px; padding-top: 20px; border-top: .5px solid var(--sep); }
.feed-foot .linkbtn { margin-top: 10px; }
.linkbtn { border: 0; background: none; color: var(--accent); padding: 6px 10px; cursor: pointer; }
.quiet { text-align: center; padding: 56px 0 40px; }
.quiet .sub { margin-top: 4px; color: var(--label2); }
.newpill {
  position: fixed; left: 50%; transform: translateX(-50%);
  bottom: 22px; z-index: 20;
  border: 0; border-radius: 999px;
  background: var(--accent); color: #FFFFFF;
  font-size: 14px; font-weight: 600; letter-spacing: -.15px;
  padding: 9px 16px; cursor: pointer;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.25);
}
`;

function head(title: string, description: string, plainBg: boolean): string {
  return `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="theme-color" media="(prefers-color-scheme: light)" content="${plainBg ? "#FFFFFF" : "#F2F2F7"}">
<meta name="theme-color" media="(prefers-color-scheme: dark)" content="#000000">
<meta name="robots" content="noindex">
<meta property="og:title" content="${title}">
<meta property="og:description" content="${description}">
<title>${title}</title>
<link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22><defs><linearGradient id=%22g%22 x1=%220%22 y1=%220%22 x2=%2232%22 y2=%2232%22 gradientUnits=%22userSpaceOnUse%22><stop stop-color=%22%236EC6FF%22/><stop offset=%221%22 stop-color=%22%230A84FF%22/></linearGradient></defs><circle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22url(%23g)%22/></svg>">
<style>${CSS}</style>`;
}

/** The response wrapper for every HTML page: locked down, never cached. */
export function htmlResponse(body: string): Response {
  return new Response(body, {
    status: 200,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "x-robots-tag": "noindex",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "content-security-policy":
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
        "connect-src 'self'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    },
  });
}

// ---- the landing ----------------------------------------------------------

export function landingPage(): string {
  return `<!doctype html>
<html lang="en">
<head>${head("Only Human", "A world server for one human.", false)}</head>
<body>
<main class="page">
  <h1 class="t-large">Only Human</h1>
  <p class="t-sub sec lede">A world server for one human.</p>
  <section class="card">
    <div class="row">
      <span class="dot" aria-hidden="true"></span>
      <div class="row-main">
        <p class="t-headline">Awake.</p>
        <p class="t-foot sec sub">Every minute, whether anyone is watching.</p>
      </div>
    </div>
    <div class="row block">
      <div class="row-main">
        <p class="t-headline">Fifteen people live here. One of them is you.</p>
        <p class="t-body sec sub">Everything they say is written the moment it happens — never
        canned, never on a schedule. When there's nothing to say, the world stays quiet.</p>
      </div>
    </div>
    <a class="row linkrow" href="/w">
      <span class="row-main"><span class="t-body" style="color:var(--accent)">Open a world</span></span>
      <svg width="8" height="14" viewBox="0 0 8 14" fill="none" aria-hidden="true"><path d="M1.5 1.5 6.5 7l-5 5.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </a>
  </section>
  <p class="t-foot foot">Only Human · world server</p>
</main>
</body>
</html>`;
}

// ---- the window -----------------------------------------------------------

const WINDOW_JS = `
(function () {
  'use strict';

  // ---- tiny helpers -------------------------------------------------------
  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }
  function icon(html) {
    var n = document.createElement('span');
    n.className = 'ic';
    n.innerHTML = html;
    return n;
  }
  var HEART = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20.3S4.7 15.8 3 11.5C1.4 7.6 3.7 4.6 6.9 4.6c2 0 3.6 1.2 5.1 3 1.5-1.8 3.1-3 5.1-3 3.2 0 5.5 3 3.9 6.9-1.7 4.3-9 8.8-9 8.8z"/></svg>';
  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'x' + Math.random().toString(36).slice(2) + Date.now().toString(36);
  }
  function clientId(kind) { return 's-' + kind + '-' + uuid(); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }

  var PALETTES = [
    ['#6EC6FF', '#0A84FF'],
    ['#FFB199', '#FF5E7E'],
    ['#62D9A5', '#16966B'],
    ['#9B8CF7', '#6B4EE0'],
    ['#F7D06B', '#F0A44B'],
    ['#F0A6CA', '#C86DD7']
  ];
  var GRAY = ['#8E8E93', '#636366'];
  var DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  // ---- state --------------------------------------------------------------
  var state = {
    world: '', key: '',
    cursor: 0, syncing: false, failed: false,
    lastSync: 0, everSynced: false, skew: 0,
    me: null, worldInfo: null, budget: null,
    personas: {}, follows: {}, posts: {}, replies: {}, stories: {},
    signals: {}, dms: {}, asks: {},
    log: [],
    outbox: [],
    drafts: {},
    times: [],
    tab: 'feed', thread: null,
    replyOpen: null, focusReply: null, focusDm: false, wantPersona: null,
    newCount: 0
  };

  function store() {
    try { localStorage.setItem('oh.world', JSON.stringify({ world: state.world, key: state.key })); } catch (e) {}
  }
  function unstore() {
    try { localStorage.removeItem('oh.world'); } catch (e) {}
  }
  function loadOutbox() {
    try {
      var all = JSON.parse(localStorage.getItem('oh.outbox') || '[]');
      state.outbox = all.filter(function (e) { return e && e.world === state.world; });
    } catch (e) { state.outbox = []; }
  }
  function saveOutbox() {
    try { localStorage.setItem('oh.outbox', JSON.stringify(state.outbox)); } catch (e) {}
  }
  function say(message) {
    var errorEl = $('error');
    errorEl.textContent = message || '';
    errorEl.hidden = !message;
  }

  // ---- the world clock ----------------------------------------------------
  function serverNow() { return Date.now() + state.skew; }
  function worldNow() {
    var w = state.worldInfo;
    return serverNow() + (w ? w.utcOffsetMinutes : 0) * 60000;
  }
  function clockText() {
    var d = new Date(worldNow());
    return pad(d.getUTCHours()) + ':' + pad(d.getUTCMinutes()) + ':' + pad(d.getUTCSeconds());
  }
  function dateText() {
    var d = new Date(worldNow());
    return DAYS[d.getUTCDay()] + ' ' + d.getUTCDate() + ' ' + MONTHS[d.getUTCMonth()];
  }
  function utcText() {
    var w = state.worldInfo;
    if (!w) return '';
    var m = w.utcOffsetMinutes;
    var sign = m < 0 ? '-' : '+';
    var a = Math.abs(m);
    return 'UTC' + sign + pad(Math.floor(a / 60)) + ':' + pad(a % 60);
  }
  function ago(t) {
    var s = Math.max(0, Math.floor((serverNow() - t) / 1000));
    if (s < 45) return 'now';
    if (s < 3600) return Math.floor(s / 60) + 'm';
    if (s < 86400) return Math.floor(s / 3600) + 'h';
    return Math.floor(s / 86400) + 'd';
  }
  function wakeText() {
    var w = state.worldInfo;
    if (!w) return '—';
    if (!w.nextTickAt) return 'resting';
    var d = w.nextTickAt - serverNow();
    if (d <= 0) return 'any minute';
    var s = Math.floor(d / 1000);
    if (s < 90) return '1m';
    if (s < 3600) return Math.floor(s / 60) + 'm';
    if (s < 86400) return Math.floor(s / 3600) + 'h';
    return Math.floor(s / 86400) + 'd';
  }
  function isQuiet() {
    var w = state.worldInfo;
    if (!w) return false;
    var d = new Date(worldNow());
    var m = d.getUTCHours() * 60 + d.getUTCMinutes();
    return m >= w.quietFrom && m < w.quietTo;
  }
  function minutesText(min) {
    var d = new Date(worldNow());
    d.setUTCHours(Math.floor(min / 60), min % 60, 0, 0);
    return pad(d.getUTCHours()) + ':' + pad(d.getUTCMinutes());
  }
  function sepText(t) {
    var w = state.worldInfo || { utcOffsetMinutes: 0 };
    var d = new Date(t + w.utcOffsetMinutes * 60000);
    var n = new Date(worldNow());
    var same = d.getUTCFullYear() === n.getUTCFullYear() && d.getUTCMonth() === n.getUTCMonth() && d.getUTCDate() === n.getUTCDate();
    var hm = pad(d.getUTCHours()) + ':' + pad(d.getUTCMinutes());
    if (same) return hm;
    var y = new Date(n.getTime() - 86400000);
    var wasY = d.getUTCFullYear() === y.getUTCFullYear() && d.getUTCMonth() === y.getUTCMonth() && d.getUTCDate() === y.getUTCDate();
    if (wasY) return 'Yesterday ' + hm;
    return d.getUTCDate() + ' ' + MONTHS[d.getUTCMonth()] + ' ' + hm;
  }

  // ---- people -------------------------------------------------------------
  function initials(name) {
    var parts = String(name || '').trim().split(/\\s+/);
    if (!parts.length || !parts[0]) return '?';
    if (parts.length === 1) return parts[0].slice(0, 1).toUpperCase();
    return (parts[0].slice(0, 1) + parts[1].slice(0, 1)).toUpperCase();
  }
  function mePersona() {
    var me = state.me || { name: 'You', handle: 'you', avatarIndex: 0 };
    return { name: me.name, handle: me.handle, gradient: PALETTES[(me.avatarIndex || 0) % PALETTES.length] };
  }
  function persona(id) {
    if (id === 'you') return mePersona();
    var p = state.personas[id];
    if (p) return { name: p.name || p.handle || id, handle: p.handle || id, gradient: p.gradient, bio: p.bio || '', joinedYear: p.joinedYear || '' };
    return { name: id, handle: id, gradient: GRAY, bio: '', joinedYear: '' };
  }
  function avatarEl(who, sizeCls) {
    var av = el('div', 'av' + (sizeCls ? ' ' + sizeCls : ''), initials(who.name));
    var g = (who.gradient && who.gradient.length >= 2) ? who.gradient : GRAY;
    av.style.setProperty('--g1', g[0]);
    av.style.setProperty('--g2', g[1]);
    return av;
  }

  // ---- api ----------------------------------------------------------------
  function act(action, payload, cmd) {
    var body = { worldId: state.world, key: state.key, action: action, commandId: cmd || uuid(), payload: payload };
    return fetch('/v1/act', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (res) {
      return res.status === 200;
    }).catch(function () { return false; });
  }
  function syncApi(cursor) {
    return fetch('/v1/sync', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ worldId: state.world, key: state.key, cursor: cursor })
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        return { status: res.status, data: data };
      });
    });
  }

  // ---- outbox (writes survive reloads; one command id per entry) ----------
  function queue(kind, fields) {
    var e = { world: state.world, kind: kind, id: clientId(kind), cmd: uuid(), at: Date.now(), status: 'queued', text: fields.text };
    if (fields.postId) e.postId = fields.postId;
    if (fields.personaId) e.personaId = fields.personaId;
    state.outbox.push(e);
    saveOutbox();
    return e;
  }
  function flush() {
    var pending = state.outbox.filter(function (e) { return e.status !== 'sending' && e.status !== 'sent'; });
    if (!pending.length) return;
    pending.forEach(function (e) {
      if (e.status === 'failed' && !e.retry) return;
      e.retry = false;
      e.status = 'sending';
      var action = e.kind === 'post' ? 'post' : e.kind === 'reply' ? 'reply' : 'dm';
      var payload;
      if (e.kind === 'post') payload = { text: e.text, id: e.id };
      else if (e.kind === 'reply') payload = { postId: e.postId, text: e.text, id: e.id };
      else payload = { personaId: e.personaId, text: e.text, id: e.id };
      act(action, payload, e.cmd).then(function (ok) {
        e.status = ok ? 'sent' : 'failed';
        saveOutbox();
        render();
        if (ok) { kickSync(4); }
      });
    });
    saveOutbox();
    render();
  }
  function pruneOutbox() {
    var before = state.outbox.length;
    state.outbox = state.outbox.filter(function (e) {
      if (e.kind === 'post') return !state.posts[e.id];
      if (e.kind === 'reply') return !state.replies[e.id];
      return !state.dms[e.id];
    });
    if (state.outbox.length !== before) saveOutbox();
  }

  // ---- sync ---------------------------------------------------------------
  function applyItems(items, replace) {
    if (replace) {
      state.personas = {}; state.follows = {}; state.posts = {}; state.replies = {};
      state.stories = {}; state.signals = {}; state.dms = {}; state.asks = {};
      state.log = [];
    }
    (items || []).forEach(function (item) {
      var d = item && item.data;
      if (!d) return;
      if (item.kind === 'persona') state.personas[d.id] = d;
      else if (item.kind === 'follow') state.follows[d.personaId] = d;
      else if (item.kind === 'post') state.posts[d.id] = d;
      else if (item.kind === 'reply') state.replies[d.id] = d;
      else if (item.kind === 'story') state.stories[d.id] = d;
      else if (item.kind === 'signal') state.signals[d.id] = d;
      else if (item.kind === 'dm') state.dms[d.id] = d;
      else if (item.kind === 'ask') state.asks[d.id] = d;
      // The log keeps every outbox item in order — even the updates that the
      // state maps collapse (an ask moving pending → approved, a post's
      // counters moving). It is the world's own record of what happened.
      if (typeof item.seq === 'number') {
        state.log.push({
          seq: item.seq,
          kind: item.kind,
          at: item.at || d.createdAt || d.at || 0,
          data: d
        });
      }
    });
  }

  function syncAll(replace, done) {
    if (state.syncing) return;
    state.syncing = true;
    state.failed = false;
    renderStatus();
    var pages = 0;
    var fresh = 0;
    function page() {
      syncApi(state.cursor).then(function (r) {
        if (r.status === 401) {
          state.syncing = false;
          unstore();
          showForm('That key doesn\\u2019t open this world.');
          return;
        }
        if (r.status !== 200) {
          state.syncing = false;
          state.failed = true;
          if (!state.everSynced) showForm('Nothing answered. Is the server awake?');
          renderStatus();
          if (done) done(false);
          return;
        }
        var items = r.data.items || [];
        applyItems(items, replace && pages === 0);
        if (r.data.me) state.me = r.data.me;
        if (typeof r.data.t === 'number') state.skew = r.data.t - Date.now();
        if (r.data.world) state.worldInfo = r.data.world;
        if (typeof r.data.budget === 'number') state.budget = r.data.budget;
        state.cursor = typeof r.data.cursor === 'number' ? r.data.cursor : state.cursor;
        state.lastSync = Date.now();
        state.everSynced = true;
        if (!(replace && pages === 0)) fresh += items.length;
        pages++;
        var more = items.length === 300 && pages < 12;
        if (more) { setTimeout(page, 150); return; }
        state.syncing = false;
        pruneOutbox();
        if (state.wantPersona && state.personas[state.wantPersona]) {
          state.thread = state.wantPersona;
          state.tab = 'dms';
        } else if (state.thread && !state.personas[state.thread]) {
          state.thread = null;
        }
        state.wantPersona = null;
        if (replace) {
          render();
          window.scrollTo(0, 0);
        } else if (fresh > 0 && (isTyping() || window.scrollY > 400)) {
          state.newCount += fresh;
          renderStatus();
          updatePill();
        } else {
          render();
        }
        flush();
        renderStatus();
        if (done) done(true);
      }).catch(function () {
        state.syncing = false;
        state.failed = true;
        if (!state.everSynced) showForm('Nothing answered. Is the server awake?');
        renderStatus();
        if (done) done(false);
      });
    }
    page();
  }
  function kickSync(seconds) {
    setTimeout(function () { if (!state.syncing) syncAll(false); }, seconds * 1000);
  }
  function isTyping() {
    var a = document.activeElement;
    return !!a && (a.tagName === 'TEXTAREA' || a.tagName === 'INPUT');
  }
  function updatePill() {
    var p = $('newpill');
    if (state.newCount > 0) {
      $('newpill-t').textContent = state.newCount + ' new \\u2014 tap to show';
      p.hidden = false;
    } else {
      p.hidden = true;
    }
  }

  // ---- render: status bar -------------------------------------------------
  function renderStatus() {
    var statusEl = $('status');
    if (!statusEl) return;
    var failedWrites = state.outbox.filter(function (e) { return e.status === 'failed'; }).length;
    var suffix = failedWrites ? ' \\u00b7 ' + failedWrites + ' not sent' : '';
    if (state.syncing) { statusEl.textContent = 'syncing\\u2026' + suffix; return; }
    if (state.failed) { statusEl.textContent = 'offline \\u2014 retrying' + suffix; return; }
    if (!state.lastSync) { statusEl.textContent = suffix.replace(/^ \\u00b7 /, ''); return; }
    var s = Math.floor((Date.now() - state.lastSync) / 1000);
    statusEl.textContent = (s < 45 ? 'synced just now' : 'synced ' + ago(state.lastSync) + ' ago') + suffix;
  }

  // ---- render: the clock --------------------------------------------------
  function renderClock() {
    var w = state.worldInfo;
    $('clock').textContent = clockText();
    $('clock-sub').textContent = dateText() + ' \\u00b7 ' + utcText() + ' \\u00b7 quiet ' + minutesText(w ? w.quietFrom : 60) + '\\u2013' + minutesText(w ? w.quietTo : 390);
    var pill = $('state-pill');
    var quiet = isQuiet();
    pill.textContent = quiet ? 'asleep' : 'awake';
    pill.className = 'pill ' + (quiet ? 'asleep' : 'awake');
    $('wake').textContent = wakeText();
    var budget = state.budget;
    var calls = w ? w.callsToday : 0;
    if (budget == null) budget = w ? w.dailyBudget : null;
    $('words').textContent = budget ? (calls + '/' + budget) : 'off';
    $('alive').textContent = w && w.createdAt ? ago(w.createdAt) : '\\u2014';
    var fill = $('meter-fill');
    var pct = budget ? Math.min(100, Math.round((calls / budget) * 100)) : 0;
    fill.style.width = pct + '%';
    fill.className = 'meter-fill' + (pct >= 80 ? ' warn' : '');
  }

  // ---- render: waiting on you ---------------------------------------------
  function renderWaiting() {
    var host = $('waiting');
    host.textContent = '';
    var asks = Object.keys(state.asks).map(function (id) { return state.asks[id]; });
    var live = asks.filter(function (a) { return a.state === 'pending' || a.state === 'approved'; });
    live.sort(function (a, b) { return (b.at || 0) - (a.at || 0); });
    if (!live.length) { host.hidden = true; return; }
    host.hidden = false;
    host.appendChild(el('h2', null, 'Waiting on you'));
    var card = el('div', 'card');
    live.forEach(function (a) {
      var who = persona(a.personaId);
      var row = el('div', 'ask');
      row.appendChild(avatarEl(who, 'md'));
      var main = el('div', 'ask-main');
      var line = el('p', 't-sub');
      var b = el('b', null, who.name);
      line.appendChild(b);
      if (a.state === 'approved') {
        line.appendChild(document.createTextNode(' is writing a reply'));
      } else {
        line.appendChild(document.createTextNode(' wants to reply to your post'));
      }
      main.appendChild(line);
      var post = state.posts[a.postId];
      if (post) {
        var q = el('div', 'ask-q');
        q.appendChild(el('p', 't-sub sec', post.text));
        main.appendChild(q);
      }
      if (a.state === 'approved') {
        var writing = el('div', 'ask-writing');
        writing.appendChild(el('span', 'spin'));
        writing.appendChild(el('span', 't-foot sec', 'It lands here when it\\u2019s written.'));
        main.appendChild(writing);
      } else {
        var actions = el('div', 'ask-actions');
        var yes = el('button', 'mini accent', 'Approve');
        yes.addEventListener('click', function () { decideAsk(a.id, true); });
        var no = el('button', 'mini ghost', 'Skip');
        no.addEventListener('click', function () { decideAsk(a.id, false); });
        actions.appendChild(yes);
        actions.appendChild(no);
        main.appendChild(actions);
      }
      row.appendChild(main);
      card.appendChild(row);
    });
    host.appendChild(card);
  }
  function decideAsk(askId, approve) {
    var ask = state.asks[askId];
    if (!ask) return;
    var prev = ask.state;
    ask.state = approve ? 'approved' : 'skipped';
    render();
    act(approve ? 'approve' : 'skip', { askId: askId }).then(function (ok) {
      if (!ok) { ask.state = prev; render(); }
      else { kickSync(approve ? 20 : 4); }
    });
  }

  // ---- render: feed -------------------------------------------------------
  function renderFeed() {
    var host = $('feed');
    host.textContent = '';
    state.times = [];

    var items = [];
    Object.keys(state.posts).forEach(function (id) {
      var p = state.posts[id];
      items.push({ at: p.createdAt || 0, seq: 0, make: function () { return postEl(p); } });
    });
    Object.keys(state.stories).forEach(function (id) {
      var s = state.stories[id];
      items.push({ at: s.createdAt || 0, seq: 0, make: function () { return eventRow(s.personaId, 'shared a story', s.createdAt, s.text); } });
    });
    Object.keys(state.signals).forEach(function (id) {
      var g = state.signals[id];
      if (g.kind === 'Reply' || g.kind === 'Message') return;
      items.push({ at: g.createdAt || 0, seq: 0, make: function () { return eventRow(g.personaId, g.rest, g.createdAt, null, g.boldPart); } });
    });
    state.outbox.forEach(function (e) {
      if (e.kind === 'post' && !state.posts[e.id]) {
        items.push({ at: e.at, seq: 0, make: function () { return postEl({ id: e.id, authorId: 'you', text: e.text, createdAt: e.at, likes: 0, replies: 0, likedByMe: false }, e); } });
      }
    });

    items.sort(function (a, b) { return b.at - a.at; });
    var shown = items.slice(0, 150);
    var first = true;
    shown.forEach(function (it) {
      var node = it.make();
      if (!first && node.className.indexOf('post') === 0) node.className += ' hair';
      first = false;
      host.appendChild(node);
    });

    var empty = items.length === 0;
    $('feed-empty').hidden = !empty;
    $('older').textContent = items.length > 150 ? '\\u2026and ' + (items.length - 150) + ' older events \\u2014 the app keeps all of it.' : '';
  }

  function postEl(p, entry) {
    var who = persona(p.authorId);
    var wrap = el('article', 'post' + (entry ? ' opt' : ''));
    wrap.appendChild(avatarEl(who));

    var body = el('div', 'post-body');
    var header = el('header');
    header.appendChild(el('span', 'name', who.name));
    var meta = el('span', 'who', '@' + who.handle + ' \\u00b7 ' + ago(p.createdAt));
    state.times.push({ el: meta, at: p.createdAt, prefix: '@' + who.handle + ' \\u00b7 ' });
    header.appendChild(meta);
    body.appendChild(header);

    body.appendChild(el('p', 'text', p.text));

    if (entry) {
      var note = el('div', 'fail-note');
      if (entry.status === 'failed') {
        note.appendChild(document.createTextNode('Not sent \\u2014 '));
        var retry = el('button', null, 'Try again');
        retry.addEventListener('click', function () { entry.retry = true; entry.status = 'queued'; flush(); });
        note.appendChild(retry);
      } else {
        note.className = 'counts';
        note.textContent = entry.status === 'sending' ? 'sending\\u2026' : entry.status === 'sent' ? 'sent\\u2026' : 'queued\\u2026';
      }
      body.appendChild(note);
      wrap.appendChild(body);
      return wrap;
    }

    var likes = p.likes || 0;
    var replies = p.replies || 0;
    if (likes > 0 || replies > 0) {
      var bits = [];
      if (likes > 0) bits.push(likes + (likes === 1 ? ' like' : ' likes'));
      if (replies > 0) bits.push(replies + (replies === 1 ? ' reply' : ' replies'));
      body.appendChild(el('div', 'counts', bits.join(' \\u00b7 ')));
    }

    var actions = el('div', 'actions');
    var likeBtn = el('button', 'act like' + (p.likedByMe ? ' liked' : ''));
    likeBtn.setAttribute('aria-label', 'Like');
    likeBtn.appendChild(icon(HEART));
    if (likes > 0) likeBtn.appendChild(el('span', null, String(likes)));
    likeBtn.addEventListener('click', function () { toggleLike(p); });
    actions.appendChild(likeBtn);
    var replyBtn = el('button', 'act rp', 'Reply');
    replyBtn.addEventListener('click', function () {
      state.replyOpen = state.replyOpen === p.id ? null : p.id;
      state.focusReply = state.replyOpen;
      render();
    });
    actions.appendChild(replyBtn);
    body.appendChild(actions);

    var mine = Object.keys(state.replies).map(function (id) { return state.replies[id]; })
      .filter(function (r) { return r.postId === p.id; })
      .sort(function (a, b) { return a.createdAt - b.createdAt; });
    var mineOut = state.outbox.filter(function (e) { return e.kind === 'reply' && e.postId === p.id && !state.replies[e.id]; });
    if (mine.length || mineOut.length) {
      var repliesEl = el('div', 'replies');
      mine.forEach(function (r) { repliesEl.appendChild(replyEl(r)); });
      mineOut.forEach(function (e) { repliesEl.appendChild(replyEl({ id: e.id, postId: p.id, authorId: 'you', text: e.text, createdAt: e.at }, e)); });
      body.appendChild(repliesEl);
    }

    if (state.replyOpen === p.id) {
      var rc = el('div', 'reply-compose');
      var ta = el('textarea', 'ta');
      ta.rows = 1;
      ta.placeholder = 'Post your reply';
      ta.maxLength = 500;
      var key = 'r:' + p.id;
      ta.value = state.drafts[key] || '';
      ta.addEventListener('input', function () {
        state.drafts[key] = ta.value;
        grow(ta);
        submit.disabled = !ta.value.trim();
      });
      rc.appendChild(ta);
      var rowEl = el('div', 'compose-row');
      var submit = el('button', 'mini accent', 'Reply');
      submit.disabled = !ta.value.trim();
      submit.addEventListener('click', function () { sendReply(p.id, ta.value); });
      rowEl.appendChild(submit);
      rc.appendChild(rowEl);
      ta.addEventListener('keydown', function (ev) {
        if ((ev.metaKey || ev.ctrlKey) && ev.key === 'Enter') sendReply(p.id, ta.value);
      });
      body.appendChild(rc);
      wrap.appendChild(body);
      requestAnimationFrame(function () { grow(ta); if (state.focusReply === p.id) { ta.focus(); state.focusReply = null; } });
      return wrap;
    }

    wrap.appendChild(body);
    return wrap;
  }

  function replyEl(r, entry) {
    var who = persona(r.authorId);
    var wrap = el('div', 'reply' + (entry ? ' opt' : ''));
    wrap.appendChild(avatarEl(who, 'sm'));
    var main = el('div', 'reply-main');
    var head = el('div', 'reply-head');
    head.appendChild(el('span', 'reply-name', who.name));
    var meta = el('span', 'reply-who', ago(r.createdAt));
    state.times.push({ el: meta, at: r.createdAt, prefix: '' });
    head.appendChild(meta);
    main.appendChild(head);
    main.appendChild(el('p', 'reply-text', r.text));
    if (entry && entry.status === 'failed') {
      var note = el('div', 'fail-note');
      note.appendChild(document.createTextNode('Not sent \\u2014 '));
      var retry = el('button', null, 'Try again');
      retry.addEventListener('click', function () { entry.retry = true; entry.status = 'queued'; flush(); });
      note.appendChild(retry);
      main.appendChild(note);
    }
    wrap.appendChild(main);
    return wrap;
  }

  function eventRow(personaId, rest, at, storyLine, boldName) {
    var who = persona(personaId);
    var wrap = el('div', 'event');
    var dot = el('span', 'event-dot');
    var g = (who.gradient && who.gradient.length >= 2) ? who.gradient : GRAY;
    dot.style.background = g[1];
    wrap.appendChild(dot);
    var t = el('div', 'event-t');
    var line = el('div', 'event-line');
    var text = el('div', 'event-text');
    text.appendChild(el('b', null, boldName || who.name));
    text.appendChild(el('span', 'rest', rest ? (' ' + rest) : ''));
    line.appendChild(text);
    var tm = el('span', 'event-time t-foot sec', ago(at));
    state.times.push({ el: tm, at: at, prefix: '' });
    line.appendChild(tm);
    t.appendChild(line);
    if (storyLine) {
      t.appendChild(el('div', 'quote story-line', '\\u201C' + storyLine + '\\u201D'));
    }
    wrap.appendChild(t);
    return wrap;
  }

  // ---- render: the log ----------------------------------------------------
  // Everything the world did, in order. Derived from the outbox stream the
  // app already receives — no extra endpoint, no extra table.
  function snippet(text, n) {
    var t = String(text || '').replace(/\\s+/g, ' ').trim();
    if (t.length <= n) return t;
    t = t.slice(0, n - 1);
    var cut = t.lastIndexOf(' ');
    if (cut > n * 0.55) t = t.slice(0, cut);
    return t + '\\u2026';
  }
  function logDayKey(t) {
    var w = state.worldInfo || { utcOffsetMinutes: 0 };
    var d = new Date(t + w.utcOffsetMinutes * 60000);
    return d.getUTCFullYear() + '-' + d.getUTCMonth() + '-' + d.getUTCDate();
  }
  function logDayLabel(t) {
    var w = state.worldInfo || { utcOffsetMinutes: 0 };
    var d = new Date(t + w.utcOffsetMinutes * 60000);
    var n = new Date(worldNow());
    if (d.getUTCFullYear() === n.getUTCFullYear() && d.getUTCMonth() === n.getUTCMonth() && d.getUTCDate() === n.getUTCDate()) return 'Today';
    var y = new Date(n.getTime() - 86400000);
    if (d.getUTCFullYear() === y.getUTCFullYear() && d.getUTCMonth() === y.getUTCMonth() && d.getUTCDate() === y.getUTCDate()) return 'Yesterday';
    return d.getUTCDate() + ' ' + MONTHS[d.getUTCMonth()];
  }
  function logTime(t) {
    var w = state.worldInfo || { utcOffsetMinutes: 0 };
    var d = new Date(t + w.utcOffsetMinutes * 60000);
    return pad(d.getUTCHours()) + ':' + pad(d.getUTCMinutes());
  }
  function logLine(e) {
    var d = e.data || {};
    var kind = e.kind;
    var at = e.at || d.createdAt || d.at || 0;
    // Follow snapshots are setup/noise; a persona's later follow already
    // arrives as a signal ("started following you").
    if (kind === 'follow') return null;
    if (kind === 'persona') {
      return { who: d.id, name: d.name || d.handle || d.id, rest: ' joined the world', at: at };
    }
    if (kind === 'post') {
      var pw = persona(d.authorId);
      return {
        who: d.authorId,
        name: d.authorId === 'you' ? 'You' : pw.name,
        rest: ' posted',
        quote: d.text,
        at: d.createdAt || at,
        key: 'post:' + d.id
      };
    }
    if (kind === 'story') {
      var sw = persona(d.personaId);
      return { who: d.personaId, name: sw.name, rest: ' shared a story', quote: d.text, at: d.createdAt || at, key: 'story:' + d.id };
    }
    if (kind === 'dm') {
      var dw = persona(d.personaId);
      if (d.fromMe) return { who: 'you', name: 'You', rest: ' messaged ' + dw.name, quote: d.text, at: d.at || at, key: 'dm:' + d.id };
      return { who: d.personaId, name: dw.name, rest: ' messaged you', quote: d.text, at: d.at || at, key: 'dm:' + d.id };
    }
    if (kind === 'reply') {
      if (d.authorId === 'you') return { who: 'you', name: 'You', rest: ' replied', quote: d.text, at: d.createdAt || at, key: 'reply:' + d.id };
      var post = state.posts[d.postId];
      var rw = persona(d.authorId);
      return {
        who: d.authorId,
        name: rw.name,
        rest: post && post.authorId === 'you' ? ' commented on your post' : ' replied',
        quote: d.text,
        at: d.createdAt || at,
        key: 'reply:' + d.id
      };
    }
    if (kind === 'ask') {
      var aw = persona(d.personaId);
      // Use the outbox item's own time: an approval/skip is stamped with the
      // ask's original time in the payload, but the log wants when it happened.
      if (d.state === 'pending') return { who: d.personaId, name: aw.name, rest: ' asked to comment on your post', at: at, key: 'ask:' + d.id + ':pending' };
      if (d.state === 'approved') return { who: 'you', name: 'You', rest: ' approved ' + aw.name + '\\u2019s comment', at: at, key: 'ask:' + d.id + ':approved' };
      if (d.state === 'skipped') return { who: 'you', name: 'You', rest: ' skipped ' + aw.name + '\\u2019s comment', at: at, key: 'ask:' + d.id + ':skipped' };
      return null; // posted — the reply itself is the line
    }
    if (kind === 'signal') {
      // Reply/Message signals mirror the reply/dm items; don't say it twice.
      if (d.kind === 'Reply' || d.kind === 'Message') return null;
      return {
        who: d.personaId,
        name: d.boldPart || persona(d.personaId).name,
        rest: d.rest || '',
        at: d.createdAt || at,
        key: 'signal:' + d.id
      };
    }
    return null;
  }
  function renderLog() {
    var host = $('log');
    host.textContent = '';
    var lines = [];
    var seen = {};
    state.log.forEach(function (e) {
      var line = logLine(e);
      if (!line) return;
      if (line.key) {
        if (seen[line.key]) return;
        seen[line.key] = true;
      }
      lines.push(line);
    });
    lines.reverse(); // newest first, like everything else here
    var shown = lines.slice(0, 400);
    if (shown.length) {
      var card = el('div', 'card logcard');
      var day = '';
      shown.forEach(function (line) {
        var key = logDayKey(line.at);
        if (key !== day) {
          day = key;
          card.appendChild(el('div', 'log-day', logDayLabel(line.at)));
        }
        var row = el('div', 'logrow');
        row.appendChild(el('span', 'log-t num', logTime(line.at)));
        var dot = el('span', 'log-dot');
        var who = persona(line.who);
        var g = (who.gradient && who.gradient.length >= 2) ? who.gradient : GRAY;
        dot.style.background = g[1];
        row.appendChild(dot);
        var x = el('div', 'log-x');
        x.appendChild(el('b', null, line.name));
        if (line.rest) x.appendChild(el('span', 'rest', line.rest));
        if (line.quote) x.appendChild(el('div', 'quote', '\\u201C' + snippet(line.quote, 140) + '\\u201D'));
        row.appendChild(x);
        card.appendChild(row);
      });
      host.appendChild(card);
    }
    $('log-empty').hidden = shown.length > 0;
    $('log-more').textContent = lines.length > shown.length ? '\\u2026and ' + (lines.length - shown.length) + ' earlier events' : '';
  }

  // ---- render: messages ---------------------------------------------------
  function conversations() {
    var by = {};
    Object.keys(state.dms).forEach(function (id) {
      var d = state.dms[id];
      var c = by[d.personaId];
      if (!c) { c = { personaId: d.personaId, last: d }; by[d.personaId] = c; }
      else if ((d.at || 0) > (c.last.at || 0)) c.last = d;
    });
    state.outbox.forEach(function (e) {
      if (e.kind !== 'dm') return;
      var c = by[e.personaId];
      if (!c) { c = { personaId: e.personaId, last: { text: e.text, at: e.at, fromMe: true } }; by[e.personaId] = c; }
      else if ((e.at || 0) > (c.last.at || 0)) c.last = { text: e.text, at: e.at, fromMe: true };
    });
    return Object.keys(by).map(function (k) { return by[k]; })
      .sort(function (a, b) { return (b.last.at || 0) - (a.last.at || 0); });
  }

  function renderDms() {
    var list = $('dm-list');
    var thread = $('dm-thread');
    if (state.thread) {
      list.hidden = true;
      thread.hidden = false;
      renderThread();
      return;
    }
    thread.hidden = true;
    list.hidden = false;
    list.textContent = '';
    var convos = conversations();
    $('dm-empty').hidden = convos.length > 0;
    convos.forEach(function (c) {
      var who = persona(c.personaId);
      var row = el('div', 'dm-row');
      row.appendChild(avatarEl(who));
      var main = el('div', 'dm-main');
      main.appendChild(el('div', 'name', who.name));
      main.appendChild(el('div', 'dm-preview t-sub sec', c.last.text || ''));
      row.appendChild(main);
      var tm = el('span', 'dm-time t-foot sec', ago(c.last.at));
      state.times.push({ el: tm, at: c.last.at, prefix: '' });
      row.appendChild(tm);
      row.addEventListener('click', function () { openThread(c.personaId); });
      list.appendChild(row);
    });
  }

  function openThread(personaId) {
    state.thread = personaId;
    state.tab = 'dms';
    switchTab('dms');
    render();
  }

  function renderThread() {
    var host = $('dm-thread');
    host.textContent = '';
    var who = persona(state.thread);
    var head = el('div', 'thread-head');
    var back = el('button', 'linkbtn t-foot', '\\u2039 Messages');
    back.addEventListener('click', function () { state.thread = null; render(); });
    head.appendChild(back);
    head.appendChild(el('span', 'thread-name t-headline', who.name));
    host.appendChild(head);

    var bubbles = el('div', 'bubbles');
    var all = [];
    Object.keys(state.dms).forEach(function (id) {
      var d = state.dms[id];
      if (d.personaId === state.thread) all.push({ at: d.at || 0, me: !!d.fromMe, text: d.text, id: d.id });
    });
    state.outbox.forEach(function (e) {
      if (e.kind === 'dm' && e.personaId === state.thread && !state.dms[e.id]) {
        all.push({ at: e.at, me: true, text: e.text, entry: e, id: e.id });
      }
    });
    all.sort(function (a, b) { return a.at - b.at; });
    var lastAt = 0;
    all.forEach(function (m) {
      if (m.at - lastAt > 30 * 60000) {
        bubbles.appendChild(el('div', 'dsep t-foot', sepText(m.at)));
      }
      lastAt = m.at;
      var b = el('div', 'bubble ' + (m.me ? 'me' : 'them'));
      b.appendChild(el('p', 'bt', m.text));
      if (m.entry && m.entry.status === 'failed') {
        var note = el('div', 'fail-note');
        note.appendChild(document.createTextNode('Not sent \\u2014 '));
        var retry = el('button', null, 'Try again');
        retry.addEventListener('click', function () { m.entry.retry = true; m.entry.status = 'queued'; flush(); });
        note.appendChild(retry);
        b.appendChild(note);
        b.style.maxWidth = '78%';
      } else if (m.entry) {
        b.style.opacity = '.55';
      }
      bubbles.appendChild(b);
    });
    host.appendChild(bubbles);

    var composer = el('div', 'dm-composer');
    var input = el('input', 'dm-in');
    input.type = 'text';
    input.placeholder = 'Message';
    input.maxLength = 500;
    input.autocomplete = 'off';
    var key = 'd:' + state.thread;
    input.value = state.drafts[key] || '';
    input.addEventListener('input', function () {
      state.drafts[key] = input.value;
      send.disabled = !input.value.trim();
    });
    input.addEventListener('keydown', function (ev) { if (ev.key === 'Enter') sendMsg(); });
    composer.appendChild(input);
    var send = el('button', 'sendbtn');
    send.setAttribute('aria-label', 'Send');
    send.innerHTML = '<svg width="15" height="15" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M8 13V3.5M8 3.5 3.5 8M8 3.5 12.5 8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    send.disabled = !input.value.trim();
    send.addEventListener('click', sendMsg);
    composer.appendChild(send);
    host.appendChild(composer);
    function sendMsg() {
      var text = input.value.trim();
      if (!text) return;
      queue('dm', { personaId: state.thread, text: text });
      state.drafts[key] = '';
      input.value = '';
      render();
      flush();
    }
    requestAnimationFrame(function () {
      if (state.focusDm) { input.focus(); state.focusDm = false; }
      composer.scrollIntoView({ block: 'end' });
    });
  }

  // ---- render: people -----------------------------------------------------
  function renderPeople() {
    var host = $('people');
    host.textContent = '';
    var ids = Object.keys(state.personas);
    ids.sort(function (a, b) { return String(state.personas[a].name).localeCompare(String(state.personas[b].name)); });
    ids.forEach(function (id) {
      var p = state.personas[id];
      var who = persona(id);
      var f = state.follows[id] || { following: false, followedBy: false };
      var row = el('div', 'person');
      row.appendChild(avatarEl(who));
      var main = el('div', 'person-main');
      var line = el('div');
      line.appendChild(el('span', 'name', who.name));
      var sub = '@' + who.handle + (f.followedBy ? ' \\u00b7 follows you' : '');
      line.appendChild(el('span', 'who', ' ' + sub));
      main.appendChild(line);
      if (p.bio) main.appendChild(el('div', 'person-bio t-sub sec', p.bio));
      row.appendChild(main);
      var btn = el('button', 'follow-btn ' + (f.following ? 'on' : 'off'), f.following ? 'Following' : 'Follow');
      btn.addEventListener('click', function (ev) {
        ev.stopPropagation();
        var on = !f.following;
        f.following = on;
        render();
        act('follow', { personaId: id, on: on }).then(function (ok) {
          if (!ok) { f.following = !on; render(); }
          else kickSync(4);
        });
      });
      row.appendChild(btn);
      row.addEventListener('click', function () { state.focusDm = true; openThread(id); });
      host.appendChild(row);
    });
  }

  // ---- render: all --------------------------------------------------------
  function render() {
    if (!state.everSynced) return;
    state.newCount = 0;
    updatePill();
    renderClock();
    renderWaiting();
    renderFeed();
    renderLog();
    renderDms();
    renderPeople();
    renderStatus();
  }

  // ---- writes -------------------------------------------------------------
  function submitPost() {
    var ta = $('post-text');
    var text = ta.value.trim();
    if (!text) return;
    queue('post', { text: text });
    ta.value = '';
    ta.style.height = 'auto';
    $('post-btn').disabled = true;
    $('post-count').textContent = '';
    render();
    window.scrollTo(0, 0);
    flush();
  }
  function sendReply(postId, value) {
    var text = value.trim();
    if (!text) return;
    queue('reply', { postId: postId, text: text });
    state.drafts['r:' + postId] = '';
    state.replyOpen = null;
    render();
    flush();
  }
  function toggleLike(p) {
    var liked = !!p.likedByMe;
    p.likedByMe = !liked;
    p.likes = Math.max(0, (p.likes || 0) + (liked ? -1 : 1));
    render();
    act('like', { postId: p.id }).then(function (ok) {
      if (!ok) {
        p.likedByMe = liked;
        p.likes = Math.max(0, (p.likes || 0) + (liked ? 1 : -1));
        render();
      } else kickSync(4);
    });
  }

  // ---- small ui helpers ---------------------------------------------------
  function grow(ta) {
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 220) + 'px';
  }

  // ---- views --------------------------------------------------------------
  function showForm(message) {
    $('open').hidden = false;
    $('console').hidden = true;
    $('lede').hidden = false;
    $('open-btn').disabled = false;
    $('open-btn').textContent = 'Open the world';
    say(message || '');
  }
  function showConsole() {
    $('open').hidden = true;
    $('console').hidden = false;
    $('lede').hidden = true;
    say('');
  }
  function switchTab(name) {
    state.tab = name;
    ['feed', 'log', 'dms', 'people'].forEach(function (t) {
      $('tab-' + t).className = 'seg-btn' + (t === name ? ' on' : '');
      $('pane-' + t).hidden = t !== name;
    });
    window.scrollTo(0, 0);
  }

  function openWorld() {
    var world = $('world-id').value.trim();
    var key = $('world-key').value.trim();
    if (!world || !key) { say('A world id and a recovery key \\u2014 both of them.'); return; }
    state.world = world;
    state.key = key;
    state.cursor = 0;
    state.posts = {}; state.replies = {}; state.personas = {}; state.follows = {};
    state.stories = {}; state.signals = {}; state.dms = {}; state.asks = {};
    state.log = [];
    state.me = null; state.worldInfo = null; state.thread = null;
    state.everSynced = false; state.lastSync = 0;
    loadOutbox();
    say('');
    $('open-btn').disabled = true;
    $('open-btn').textContent = 'Opening\\u2026';
    showConsole();
    syncAll(true, function (ok) {
      if (ok) {
        store();
        switchTab(state.tab || 'feed');
        state.focusDm = false;
        render();
      }
    });
  }

  // ---- wiring -------------------------------------------------------------
  $('open-btn').addEventListener('click', openWorld);
  $('world-id').addEventListener('keydown', function (e) { if (e.key === 'Enter') openWorld(); });
  $('world-key').addEventListener('keydown', function (e) { if (e.key === 'Enter') openWorld(); });
  $('forget').addEventListener('click', function () {
    unstore();
    try { localStorage.removeItem('oh.outbox'); } catch (e) {}
    state.world = ''; state.key = ''; state.cursor = 0;
    state.posts = {}; state.replies = {}; state.personas = {}; state.follows = {};
    state.stories = {}; state.signals = {}; state.dms = {}; state.asks = {};
    state.log = [];
    state.me = null; state.worldInfo = null; state.outbox = [];
    state.thread = null; state.everSynced = false; state.lastSync = 0;
    state.drafts = {}; state.times = [];
    $('world-id').value = ''; $('world-key').value = '';
    $('post-text').value = ''; $('post-text').style.height = 'auto';
    $('post-btn').disabled = true; $('post-count').textContent = '';
    showForm('');
  });
  ['feed', 'log', 'dms', 'people'].forEach(function (t) {
    $('tab-' + t).addEventListener('click', function () {
      switchTab(t);
      render();
    });
  });
  $('newpill').addEventListener('click', function () {
    state.newCount = 0;
    updatePill();
    render();
    window.scrollTo(0, 0);
  });
  var postTa = $('post-text');
  postTa.addEventListener('input', function () {
    state.drafts['post'] = postTa.value;
    grow(postTa);
    var n = postTa.value.length;
    $('post-count').textContent = n > 380 ? n + '/500' : '';
    $('post-btn').disabled = !postTa.value.trim();
  });
  postTa.addEventListener('keydown', function (ev) {
    if ((ev.metaKey || ev.ctrlKey) && ev.key === 'Enter') submitPost();
  });
  $('post-btn').addEventListener('click', submitPost);

  // ---- boot ---------------------------------------------------------------
  var launched = false;
  try {
    var frag = new URLSearchParams(location.hash.replace(/^#/, ''));
    var fw = frag.get('world');
    var fk = frag.get('key');
    if (fw && fk) {
      $('world-id').value = fw;
      $('world-key').value = fk;
      var ftab = frag.get('tab');
      if (ftab === 'dms' || ftab === 'messages') state.tab = 'dms';
      else if (ftab === 'people') state.tab = 'people';
      else if (ftab === 'log') state.tab = 'log';
      var fpersona = frag.get('persona');
      if (fpersona) { state.wantPersona = fpersona; state.tab = 'dms'; }
      history.replaceState(null, '', location.pathname);
      launched = true;
      openWorld();
    }
  } catch (e) {}

  if (!launched) {
    try {
      var saved = JSON.parse(localStorage.getItem('oh.world') || 'null');
      if (saved && saved.world && saved.key) {
        $('world-id').value = saved.world;
        $('world-key').value = saved.key;
        launched = true;
        openWorld();
      }
    } catch (e) {}
  }

  if (!launched) showForm('');

  // A quiet beat: the clock ticks, times stay honest, and the world is pulled.
  setInterval(function () {
    if (!state.everSynced) return;
    renderClock();
    state.times.forEach(function (t) { t.el.textContent = t.prefix + ago(t.at); });
    if (!state.syncing) renderStatus();
  }, 1000);
  setInterval(function () {
    if (!$('console').hidden && !state.syncing) syncAll(false);
  }, 40000);
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden && !$('console').hidden && Date.now() - state.lastSync > 40000) syncAll(false);
  });
})();
`;

export function windowPage(): string {
  return `<!doctype html>
<html lang="en">
<head>${head("The world — Only Human", "The same world, from any browser.", true)}</head>
<body class="plain">
<header class="bar">
  <a class="back t-body" href="/">
    <svg width="12" height="20" viewBox="0 0 12 20" fill="none" aria-hidden="true"><path d="M10.5 1.5 2 10l8.5 8.5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
    Only Human
  </a>
  <span id="status" class="status t-foot sec"></span>
</header>
<main class="page window">
  <h1 class="t-large">The world</h1>
  <p id="lede" class="t-sub sec lede">Paste the world id and recovery key from the app — Settings → World server.</p>

  <section id="open" class="card formcard" hidden>
    <div class="field">
      <label class="t-foot sec" for="world-id">World id</label>
      <input id="world-id" class="mono" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="xxxxxxxx-xxxx-…">
    </div>
    <div class="field">
      <label class="t-foot sec" for="world-key">Recovery key</label>
      <input id="world-key" class="mono" type="password" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="ABCD-EFGH-…">
    </div>
    <p id="error" class="t-foot err" hidden></p>
    <button id="open-btn" class="btn">Open the world</button>
    <p class="t-foot sec note">Your key stays in this browser and goes only to your server.</p>
  </section>

  <section id="console" hidden>
    <div class="card statuscard">
      <div class="status-top">
        <div>
          <div id="clock" class="clock num">--:--:--</div>
          <div id="clock-sub" class="t-foot sec clock-sub"></div>
        </div>
        <span id="state-pill" class="pill">…</span>
      </div>
      <div class="stats">
        <div class="stat"><div id="wake" class="stat-v">—</div><div class="stat-k t-foot sec">next wake</div></div>
        <div class="stat"><div id="words" class="stat-v">—</div><div class="stat-k t-foot sec">their words today</div></div>
        <div class="stat"><div id="alive" class="stat-v">—</div><div class="stat-k t-foot sec">alive</div></div>
      </div>
      <div class="meter"><div id="meter-fill" class="meter-fill"></div></div>
    </div>

    <div class="seg" role="tablist">
      <button id="tab-feed" class="seg-btn on" role="tab">Feed</button>
      <button id="tab-log" class="seg-btn" role="tab">Log</button>
      <button id="tab-dms" class="seg-btn" role="tab">Messages</button>
      <button id="tab-people" class="seg-btn" role="tab">People</button>
    </div>

    <section id="pane-feed">
      <div id="waiting" class="waiting" hidden></div>
      <div class="card compose">
        <textarea id="post-text" class="ta" rows="1" maxlength="500" placeholder="What's on your mind?"></textarea>
        <div class="compose-row">
          <span id="post-count" class="t-foot sec count"></span>
          <button id="post-btn" class="mini accent" disabled>Post</button>
        </div>
      </div>
      <div id="feed"></div>
      <p id="feed-empty" class="t-sub quiet" hidden>Quiet right now.<br><span class="t-foot sec sub" style="display:inline-block">The world speaks when it has something to say.</span></p>
      <p id="older" class="t-foot sec older"></p>
    </section>

    <section id="pane-log" hidden>
      <div id="log"></div>
      <p id="log-empty" class="t-sub quiet" hidden>Nothing has happened yet.</p>
      <p id="log-more" class="t-foot sec older"></p>
    </section>

    <section id="pane-dms" hidden>
      <div id="dm-list"></div>
      <p id="dm-empty" class="t-sub quiet" hidden>No messages yet.<br><span class="t-foot sec sub" style="display:inline-block">Tap someone in People to start one.</span></p>
      <div id="dm-thread" hidden></div>
    </section>

    <section id="pane-people" hidden>
      <div id="people"></div>
    </section>

    <footer class="feed-foot">
      <p class="t-foot sec">The same world the app sees — everything you write here is written for real.</p>
      <button id="forget" class="linkbtn t-foot">Forget this world</button>
    </footer>
  </section>
</main>
<button id="newpill" class="newpill" hidden><span id="newpill-t"></span></button>
<script>${WINDOW_JS}</script>
</body>
</html>`;
}
