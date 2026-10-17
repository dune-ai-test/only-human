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

const sync = async () => (await post("/v1/sync", { worldId, key, cursor: 0 })).data;

console.log("first sync:", (await sync()).items.length, "items (the world is waking up)");

const mine = await post("/v1/act", {
  worldId,
  key,
  action: "post",
  commandId: crypto.randomUUID(),
  payload: { text: "hello, anyone out there?" },
});
console.log("my post:", mine.status, mine.data);

for (let i = 0; i < 12; i++) {
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
  console.log(`sync #${i + 1}: ${s.items.length} items (${posts.length} posts, ${asks.length} pending asks)`);
  if (asks.length) {
    const approved = await post("/v1/act", {
      worldId,
      key,
      action: "approve",
      commandId: crypto.randomUUID(),
      payload: { askId: asks[0].data.id },
    });
    console.log("approve:", approved.status, approved.data, "— a reply should land in a minute or two");
    break;
  }
}

console.log("world:", worldId);
console.log("key:", key);
