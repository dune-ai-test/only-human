// Smokes the world API end to end against a running worker.
//   # another terminal, in server/:
//   npm run schema:local
//   npx wrangler dev --test-scheduled
//   node scripts/smoke.mjs http://127.0.0.1:8787 --pulse
//
// --pulse also hits /__scheduled between syncs so the every-minute heartbeat
// runs locally without waiting for the real cron.
const base = (process.argv[2] || "http://127.0.0.1:8787").replace(/\/+$/, "");
const pulse = process.argv.includes("--pulse");

const personas = [
  {
    id: "sam",
    name: "Sam Okafor",
    handle: "sam",
    bio: "runs at dawn, draws at night",
    occupation: "illustrator",
    location: "Lisbon",
    voice: "warm, a little dry",
    gradient: ["#62D9A5", "#16966B"],
    joinedYear: "2019",
    followerBoost: 120,
  },
  {
    id: "mira",
    name: "Mira Lindqvist",
    handle: "mirawrites",
    bio: "libraries and long walks",
    occupation: "librarian",
    location: "Malmö",
    voice: "precise, kind",
    gradient: ["#9B8CF7", "#6B4EE0"],
    joinedYear: "2021",
    followerBoost: 90,
  },
  {
    id: "teo",
    name: "Teo Álvarez",
    handle: "teo",
    bio: "makes coffee, makes trouble",
    occupation: "barista",
    location: "Mexico City",
    voice: "fast, funny",
    gradient: ["#F7D06B", "#F0A44B"],
    joinedYear: "2020",
    followerBoost: 60,
  },
];

async function post(path, body) {
  const res = await fetch(`${base}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

console.log("health:", await (await fetch(`${base}/v1/health`)).json());

// The site: the landing answers, and the console window is there to open.
const home = await fetch(`${base}/`);
const homeText = await home.text();
console.log(
  "site:",
  home.status,
  home.headers.get("content-type"),
  homeText.includes("Only Human") && homeText.includes("Open a world")
    ? "landing ok"
    : "LANDING MISSING COPY",
);
const win = await fetch(`${base}/w`);
const winText = await win.text();
console.log(
  "window:",
  win.status,
  winText.includes("Open the world") &&
    winText.includes("What's on your mind?") &&
    winText.includes(">People<") &&
    winText.includes('id="clock"')
    ? "console window ok"
    : "WINDOW MISSING COPY",
);
if (home.status !== 200 || win.status !== 200) process.exit(1);

const created = await post("/v1/worlds", {
  me: { name: "Robin", handle: "robin", avatarIndex: 0 },
  utcOffsetMinutes: -new Date().getTimezoneOffset(),
  personas,
  relations: [{ a: "sam", b: "mira", label: "old friends" }],
  following: { sam: true, mira: true, teo: false },
  followedBy: { sam: true, mira: true, teo: false },
});
console.log("create:", created.status, created.data.worldId ? "world + key ok" : created.data);
const { worldId, key } = created.data;
if (!worldId) process.exit(1);

const sync = async (cursor = 0) => (await post("/v1/sync", { worldId, key, cursor })).data;

const first = await sync();
console.log("first sync:", first.items.length, "items (the world is waking up)");
console.log("me:", first.me, "budget:", first.budget);
console.log(
  "cast shipped:",
  first.items.filter((it) => it.kind === "persona").length,
  "personas,",
  first.items.filter((it) => it.kind === "follow").length,
  "follows",
);

// The phone makes ids so its copy shows instantly; the world adopts them.
const clientPostId = "s-post-" + crypto.randomUUID();
const mine = await post("/v1/act", {
  worldId,
  key,
  action: "post",
  commandId: crypto.randomUUID(),
  payload: { id: clientPostId, text: "hello, anyone out there?" },
});
const adopted = (await sync()).items.some((it) => it.kind === "post" && it.data.id === clientPostId);
console.log("my post:", mine.status, mine.data, "— phone id adopted:", adopted);

const modeled = await post("/v1/act", {
  worldId,
  key,
  action: "set_model",
  commandId: crypto.randomUUID(),
  payload: { personaId: "sam", model: "poolside/laguna-s-2.1:free" },
});
const modelItem = (await sync()).items.find((it) => it.kind === "model");
console.log("set_model:", modeled.status, "— model item:", modelItem?.data ?? "MISSING");

const joined = await post("/v1/act", {
  worldId,
  key,
  action: "create_persona",
  commandId: crypto.randomUUID(),
  payload: {
    id: "ai-nadiawrites",
    name: "Nadia",
    handle: "nadiawrites",
    bio: "new in town",
    occupation: "archivist",
    location: "Utrecht",
    voice: "quiet, curious",
    gradient: ["#FFB199", "#FF5E7E"],
    joinedYear: "2026",
    followerBoost: 0,
  },
});
const afterJoin = (await sync()).items;
const joinFollow = afterJoin.find((it) => it.kind === "follow" && it.data.personaId === "ai-nadiawrites");
const joinSignal = afterJoin.find((it) => it.kind === "signal" && String(it.data.rest).includes("just moved in"));
console.log(
  "create_persona:",
  joined.status,
  joined.data.id,
  "— follow:",
  joinFollow?.data ?? "MISSING",
  "— join signal:",
  joinSignal ? "ok" : "MISSING",
);

let approved = false;
for (let i = 0; i < 18; i++) {
  await new Promise((r) => setTimeout(r, 10_000));
  if (pulse) {
    try {
      await fetch(`${base}/__scheduled?cron=*+*+*+*+*`, { signal: AbortSignal.timeout(60_000) });
    } catch {
      // heartbeat missed — next loop will pulse again
    }
  }
  const s = await sync();
  const asks = s.items.filter((it) => it.kind === "ask" && it.data.state === "pending");
  const posts = s.items.filter((it) => it.kind === "post");
  const replies = s.items.filter((it) => it.kind === "reply");
  console.log(
    `sync #${i + 1}: ${s.items.length} items (${posts.length} posts, ${replies.length} replies, ${asks.length} pending asks)`,
  );
  if (!approved && asks.length) {
    const done = await post("/v1/act", {
      worldId,
      key,
      action: "approve",
      commandId: crypto.randomUUID(),
      payload: { askId: asks[0].data.id },
    });
    console.log("approve:", done.status, done.data, "— waiting for the real words");
    approved = true;
    continue;
  }
  if (approved && replies.length) {
    const reply = replies[replies.length - 1];
    console.log("reply landed:", reply.data.authorId, JSON.stringify(reply.data.text));
    break;
  }
}

console.log("world:", worldId);
console.log("key:", key);
