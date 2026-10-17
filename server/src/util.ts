/** Small shared helpers: responses, ids, keys, time. */

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "content-type",
      "access-control-allow-methods": "GET, POST, OPTIONS",
    },
  });
}

export function now(): number {
  return Date.now();
}

export function randInt(min: number, max: number): number {
  return Math.floor(min + Math.random() * (max - min + 1));
}

export function pick<T>(arr: T[]): T | null {
  return arr.length ? arr[randInt(0, arr.length - 1)] : null;
}

export function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}

export function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

export function newId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${crypto.randomUUID().slice(0, 8)}`;
}

/** Crockford-style base32, no I/L/O/U — readable when typed by a human. */
const B32 = "ABCDEFGHJKMNPQRSTVWXYZ0123456789";

export function newRecoveryKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(20));
  let out = "";
  for (const b of bytes) out += B32[b % 32];
  return (out.match(/.{1,4}/g) ?? [out]).join("-");
}

export async function hashKey(key: string): Promise<string> {
  const clean = key.replace(/[^a-z0-9]/gi, "").toUpperCase();
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clean));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function sameHash(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

// ---- world time ----------------------------------------------------------

export function dayKey(t: number, utcOffsetMin: number): string {
  const d = new Date(t + utcOffsetMin * 60_000);
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${d.getUTCFullYear()}-${m}-${day}`;
}

export function minuteOfDay(t: number, utcOffsetMin: number): number {
  const d = new Date(t + utcOffsetMin * 60_000);
  return d.getUTCHours() * 60 + d.getUTCMinutes();
}

/** The world sleeps roughly 01:00–06:30, local to the human who made it. */
export function quietNow(t: number, utcOffsetMin: number): boolean {
  const m = minuteOfDay(t, utcOffsetMin);
  return m >= 60 && m < 390;
}

/** The next 06:45–07:10 local morning, as epoch millis. */
export function nextMorning(t: number, utcOffsetMin: number): number {
  const local = new Date(t + utcOffsetMin * 60_000);
  local.setUTCHours(0, 0, 0, 0);
  const midnight = local.getTime() - utcOffsetMin * 60_000;
  const target = 6 * 60 + 45 + randInt(0, 25);
  const days = minuteOfDay(t, utcOffsetMin) >= target ? 1 : 0;
  return midnight + (days * 24 * 60 + target) * 60_000;
}
