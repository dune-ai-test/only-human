/**
 * The small web frontend, served by the Worker itself — no assets, no build
 * step, no dependencies. Two pages:
 *
 *   GET /   — the landing: what this server is, in as few words as possible.
 *   GET /w  — the window: paste a world id + recovery key and read the feed,
 *             read-only, from any browser. The key travels only in the POST
 *             body (the deep-link variant keeps it in the URL fragment, which
 *             browsers never send anywhere).
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
  --press: rgba(0, 0, 0, 0.05);
  --bar: rgba(242, 242, 247, 0.82);
  --ring: rgba(0, 122, 255, 0.3);
  --sel: rgba(0, 122, 255, 0.22);
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
    --press: rgba(255, 255, 255, 0.07);
    --bar: rgba(0, 0, 0, 0.72);
    --ring: rgba(10, 132, 255, 0.36);
    --sel: rgba(10, 132, 255, 0.3);
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
.back { display: inline-flex; align-items: center; gap: 5px; margin-left: -4px; }
.back svg { display: block; }
.status { margin-left: auto; }
.window { max-width: 600px; padding: 10px 16px 48px; }
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

#feed { margin-top: 10px; }
.post { position: relative; display: flex; gap: 12px; padding: 14px 0; }
.post + .post::before {
  content: ""; position: absolute; top: 0; left: 56px; right: 0;
  border-top: .5px solid var(--sep);
}
.av {
  flex: none; width: 44px; height: 44px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #FFFFFF; font-size: 17px; font-weight: 600; letter-spacing: .3px;
  background-image: linear-gradient(180deg, rgba(255, 255, 255, .22), rgba(255, 255, 255, 0) 55%), linear-gradient(135deg, var(--g1), var(--g2));
}
.post-body { flex: 1; min-width: 0; }
.post-body header { display: flex; flex-wrap: wrap; align-items: baseline; column-gap: 6px; }
.name { font-size: 17px; line-height: 22px; font-weight: 600; letter-spacing: -.41px; }
.who { font-size: 15px; line-height: 20px; letter-spacing: -.24px; color: var(--label2); }
.text { margin: 3px 0 0; font-size: 16px; line-height: 22px; letter-spacing: -.32px; white-space: pre-wrap; overflow-wrap: anywhere; }
.counts { margin-top: 6px; font-size: 13px; line-height: 18px; letter-spacing: -.08px; color: var(--label2); }
.quiet { text-align: center; padding: 56px 0 40px; }
.quiet .sub { margin-top: 4px; color: var(--label2); }
.older { text-align: center; margin-top: 18px; }
.feed-foot { text-align: center; margin-top: 36px; padding-top: 20px; border-top: .5px solid var(--sep); }
.feed-foot .linkbtn { margin-top: 10px; }
.linkbtn { border: 0; background: none; color: var(--accent); padding: 6px 10px; cursor: pointer; }
`;

function head(title: string, description: string, windowPage: boolean): string {
  return `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="theme-color" media="(prefers-color-scheme: light)" content="${windowPage ? "#FFFFFF" : "#F2F2F7"}">
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
  var el = function (id) { return document.getElementById(id); };
  var form = el('open');
  var feedWrap = el('feed-wrap');
  var feed = el('feed');
  var statusEl = el('status');
  var lede = el('lede');
  var errorEl = el('error');
  var btn = el('open-btn');
  var worldInput = el('world-id');
  var keyInput = el('world-key');
  var emptyEl = el('empty');
  var olderEl = el('older');

  var PALETTES = [
    ['#6EC6FF', '#0A84FF'],
    ['#FFB199', '#FF5E7E'],
    ['#62D9A5', '#16966B'],
    ['#9B8CF7', '#6B4EE0'],
    ['#F7D06B', '#F0A44B'],
    ['#F0A6CA', '#C86DD7']
  ];

  var state = {
    world: '', key: '',
    cursor: 0, syncing: false, failed: false,
    lastSync: 0, everSynced: false,
    me: null, personas: {}, posts: {}, times: []
  };

  function store() {
    try { localStorage.setItem('oh.world', JSON.stringify({ world: state.world, key: state.key })); } catch (e) {}
  }
  function unstore() {
    try { localStorage.removeItem('oh.world'); } catch (e) {}
  }

  function say(message) {
    errorEl.textContent = message || '';
    errorEl.hidden = !message;
  }

  function ago(t) {
    var s = Math.max(0, Math.floor((Date.now() - t) / 1000));
    if (s < 45) return 'now';
    if (s < 3600) return Math.floor(s / 60) + 'm';
    if (s < 86400) return Math.floor(s / 3600) + 'h';
    return Math.floor(s / 86400) + 'd';
  }

  function initials(name) {
    var parts = String(name || '').trim().split(/\\s+/);
    if (!parts.length || !parts[0]) return '?';
    if (parts.length === 1) return parts[0].slice(0, 1).toUpperCase();
    return (parts[0].slice(0, 1) + parts[1].slice(0, 1)).toUpperCase();
  }

  function persona(id) {
    if (id === 'you') {
      var me = state.me || { name: 'You', handle: 'you', avatarIndex: 0 };
      return { name: me.name, handle: me.handle, gradient: PALETTES[(me.avatarIndex || 0) % PALETTES.length] };
    }
    var p = state.personas[id];
    if (p) return { name: p.name || p.handle || id, handle: p.handle || id, gradient: p.gradient };
    return { name: id, handle: id, gradient: ['#8E8E93', '#636366'] };
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

  function applyItems(items, replace) {
    if (replace) { state.personas = {}; state.posts = {}; }
    (items || []).forEach(function (item) {
      var d = item && item.data;
      if (!d || !d.id && item.kind !== 'persona') return;
      if (item.kind === 'persona') {
        state.personas[d.id] = { name: d.name, handle: d.handle, gradient: d.gradient };
      } else if (item.kind === 'post') {
        state.posts[d.id] = d;
      }
    });
  }

  function syncAll(replace, done) {
    if (state.syncing) return;
    state.syncing = true;
    state.failed = false;
    renderStatus();
    var pages = 0;
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
        applyItems(r.data.items, replace && pages === 0);
        if (r.data.me) state.me = r.data.me;
        state.cursor = typeof r.data.cursor === 'number' ? r.data.cursor : state.cursor;
        state.lastSync = Date.now();
        state.everSynced = true;
        pages++;
        var more = (r.data.items || []).length === 300 && pages < 12;
        if (more) { setTimeout(page, 150); return; }
        state.syncing = false;
        render();
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

  function avatarEl(who) {
    var av = document.createElement('div');
    av.className = 'av';
    av.textContent = initials(who.name);
    var g = (who.gradient && who.gradient.length >= 2) ? who.gradient : ['#8E8E93', '#636366'];
    av.style.setProperty('--g1', g[0]);
    av.style.setProperty('--g2', g[1]);
    return av;
  }

  function postEl(p) {
    var who = persona(p.authorId);
    var wrap = document.createElement('article');
    wrap.className = 'post';

    var body = document.createElement('div');
    body.className = 'post-body';

    var header = document.createElement('header');
    var name = document.createElement('span');
    name.className = 'name';
    name.textContent = who.name;
    var meta = document.createElement('span');
    meta.className = 'who';
    meta.textContent = '@' + who.handle + ' · ' + ago(p.createdAt);
    header.appendChild(name);
    header.appendChild(meta);

    var text = document.createElement('p');
    text.className = 'text';
    text.textContent = p.text;

    body.appendChild(header);
    body.appendChild(text);

    var likes = p.likes || 0;
    var replies = p.replies || 0;
    if (likes > 0 || replies > 0) {
      var bits = [];
      if (likes > 0) bits.push(likes + (likes === 1 ? ' like' : ' likes'));
      if (replies > 0) bits.push(replies + (replies === 1 ? ' reply' : ' replies'));
      var counts = document.createElement('div');
      counts.className = 'counts';
      counts.textContent = bits.join(' · ');
      body.appendChild(counts);
    }

    wrap.appendChild(avatarEl(who));
    wrap.appendChild(body);
    state.times.push({ el: meta, at: p.createdAt, handle: who.handle });
    return wrap;
  }

  function render() {
    var list = Object.keys(state.posts).map(function (id) { return state.posts[id]; });
    list.sort(function (a, b) { return (b.createdAt || 0) - (a.createdAt || 0); });
    feed.textContent = '';
    state.times = [];
    list.slice(0, 100).forEach(function (p) { feed.appendChild(postEl(p)); });
    emptyEl.hidden = list.length > 0;
    olderEl.textContent = list.length > 100
      ? '…and ' + (list.length - 100) + ' older posts — the app shows everything.'
      : '';
  }

  function renderStatus() {
    if (!statusEl) return;
    if (state.syncing) { statusEl.textContent = 'syncing…'; return; }
    if (state.failed) { statusEl.textContent = 'offline — retrying'; return; }
    if (!state.lastSync) { statusEl.textContent = ''; return; }
    var s = Math.floor((Date.now() - state.lastSync) / 1000);
    statusEl.textContent = s < 45 ? 'synced just now' : 'synced ' + ago(state.lastSync) + ' ago';
  }

  function showForm(message) {
    form.hidden = false;
    feedWrap.hidden = true;
    lede.hidden = false;
    btn.disabled = false;
    btn.textContent = 'Open the world';
    say(message || '');
  }

  function showFeed() {
    form.hidden = true;
    feedWrap.hidden = false;
    lede.hidden = true;
    say('');
  }

  function open() {
    var world = worldInput.value.trim();
    var key = keyInput.value.trim();
    if (!world || !key) { say('A world id and a recovery key — both of them.'); return; }
    state.world = world;
    state.key = key;
    state.cursor = 0;
    state.posts = {};
    state.personas = {};
    state.times = [];
    say('');
    btn.disabled = true;
    btn.textContent = 'Opening…';
    showFeed();
    syncAll(true, function (ok) {
      if (ok) store();
    });
  }

  btn.addEventListener('click', open);
  worldInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') open(); });
  keyInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') open(); });
  el('forget').addEventListener('click', function () {
    unstore();
    state.world = ''; state.key = ''; state.cursor = 0;
    state.posts = {}; state.personas = {}; state.me = null;
    state.everSynced = false; state.lastSync = 0;
    worldInput.value = ''; keyInput.value = '';
    showForm('');
  });

  // Deep link: /w#world=<id>&key=<key> — the fragment never leaves the
  // browser, and it is scrubbed from the address bar right away.
  try {
    var frag = new URLSearchParams(location.hash.replace(/^#/, ''));
    var fw = frag.get('world');
    var fk = frag.get('key');
    if (fw && fk) {
      worldInput.value = fw;
      keyInput.value = fk;
      history.replaceState(null, '', location.pathname);
      open();
      return;
    }
  } catch (e) {}

  try {
    var saved = JSON.parse(localStorage.getItem('oh.world') || 'null');
    if (saved && saved.world && saved.key) {
      worldInput.value = saved.world;
      keyInput.value = saved.key;
      open();
      return;
    }
  } catch (e) {}

  showForm('');

  // A quiet beat: keep the times honest and pull whatever is new.
  setInterval(function () {
    renderStatus();
    state.times.forEach(function (t) {
      t.el.textContent = '@' + t.handle + ' · ' + ago(t.at);
    });
  }, 15000);
  setInterval(function () {
    if (!feedWrap.hidden) syncAll(false);
  }, 40000);
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden && !feedWrap.hidden && Date.now() - state.lastSync > 40000) syncAll(false);
  });
})();
`;

export function windowPage(): string {
  return `<!doctype html>
<html lang="en">
<head>${head("The world — Only Human", "A read-only window into a world.", true)}</head>
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
    <p class="t-foot sec note">Read-only. Nothing on this page writes to the world.</p>
  </section>

  <section id="feed-wrap" hidden>
    <div id="feed"></div>
    <p id="empty" class="t-sub quiet" hidden>Quiet right now.<br><span class="t-foot sec sub" style="display:inline-block">The world speaks when it has something to say.</span></p>
    <p id="older" class="t-foot sec older"></p>
    <footer class="feed-foot">
      <p class="t-foot sec">Read-only window. Words are only ever written in the app.</p>
      <button id="forget" class="linkbtn t-foot">Forget this world</button>
    </footer>
  </section>
</main>
<script>${WINDOW_JS}</script>
</body>
</html>`;
}
