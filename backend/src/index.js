/**
 * Focus sync worker.
 *
 * Five endpoints, one job: hold an encrypted copy of a phone's data so a reinstall can get it
 * back. The server never sees plaintext. It stores blobs it cannot open, keyed by an email
 * address, and hands them back to whoever can prove they know the password.
 *
 * The proof is a "verifier" -- a hash of the password under a salt that is different from the
 * one used to derive the encryption key. Holding every verifier in this database gets you no
 * closer to reading a single blob.
 *
 * This is deliberately not a general-purpose account system. There is no email confirmation,
 * no reset, no recovery: with end-to-end encryption there cannot be. Lose the password and the
 * data is gone, which is the price of the server not being able to read it.
 */

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });

const bad = (message, status = 400) => json({ ok: false, error: message }, status);

/** The collections a client may sync. An unknown name is rejected rather than stored. */
const COLLECTIONS = new Set([
  "habits",
  "habit_completions",
  "habit_debt",
  "goals",
  "reminders",
  "resources",
  "links",
  "home_apps",
  "whitelist",
  "focus_stats",
  "pomodoro",
  "session",
  "link_gate",
  "watch_progress",
  "app_limits",
  "collections",
  "alarms",
]);

const nowMs = () => Date.now();

/** Stores the enrolled face (already encrypted client-side) for an account. */
async function handleFaceUpload(request, env) {
  const body = await readBody(request);
  const user = await authenticate(env, body);
  if (!user) return bad("Email or password is wrong.", 401);

  const ciphertext = body?.ciphertext;
  const iv = body?.iv;
  if (typeof ciphertext !== "string" || typeof iv !== "string") {
    return bad("Malformed face payload.");
  }
  // A face JPEG in base64, then encrypted, is comfortably under this.
  if (ciphertext.length > 3_000_000) return bad("Face image too large.");

  await env.DB.prepare(
    "INSERT INTO blobs (email, collection, ciphertext, iv, revision, updated_at) " +
      "VALUES (?, 'face', ?, ?, 0, ?) " +
      "ON CONFLICT(email, collection) DO UPDATE SET " +
      "ciphertext = excluded.ciphertext, iv = excluded.iv, updated_at = excluded.updated_at"
  )
    .bind(user.email, ciphertext, iv, nowMs())
    .run();

  return json({ ok: true });
}

/** Hands the enrolled face back to a phone that has lost it. */
async function handleFaceDownload(request, env) {
  const body = await readBody(request);
  const user = await authenticate(env, body);
  if (!user) return bad("Email or password is wrong.", 401);

  const row = await env.DB.prepare(
    "SELECT ciphertext, iv FROM blobs WHERE email = ? AND collection = 'face'"
  )
    .bind(user.email)
    .first();

  return json({ ok: true, face: row ?? null });
}

/** Constant-time string compare, so a verifier cannot be guessed a character at a time. */
function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function randomHex(bytes) {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  return [...buf].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const cleanEmail = (raw) =>
  typeof raw === "string" ? raw.trim().toLowerCase() : "";

/** Rejects anything that is not plausibly an address, before it reaches the database. */
const emailLooksReal = (e) =>
  e.length > 2 && e.length <= 254 && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(e);

async function readBody(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

/** Looks the account up and checks the verifier. Returns the row, or null. */
async function authenticate(env, body) {
  const email = cleanEmail(body?.email);
  const verifier = body?.verifier;
  if (!emailLooksReal(email) || typeof verifier !== "string" || !verifier) return null;

  const row = await env.DB.prepare(
    "SELECT email, verifier, key_salt FROM users WHERE email = ?"
  )
    .bind(email)
    .first();

  if (!row || !safeEqual(row.verifier, verifier)) return null;

  await env.DB.prepare("UPDATE users SET last_seen_at = ? WHERE email = ?")
    .bind(nowMs(), email)
    .run();

  return row;
}

// ---------------------------------------------------------------- endpoints

/**
 * Creates an account, or hands back the salt for one that exists.
 *
 * A repeat signup with the correct verifier is treated as a sign-in rather than an error: the
 * app calls this on a fresh install, where "already registered" is the normal case and not a
 * failure the person needs to see.
 */
async function handleRegister(request, env) {
  const body = await readBody(request);
  const email = cleanEmail(body?.email);
  const verifier = body?.verifier;

  if (!emailLooksReal(email)) return bad("That does not look like an email address.");
  if (typeof verifier !== "string" || verifier.length < 32) {
    return bad("Missing or malformed verifier.");
  }

  const existing = await env.DB.prepare(
    "SELECT email, verifier, key_salt FROM users WHERE email = ?"
  )
    .bind(email)
    .first();

  if (existing) {
    if (!safeEqual(existing.verifier, verifier)) {
      return bad("An account with that email already exists.", 409);
    }
    return json({ ok: true, existing: true, keySalt: existing.key_salt });
  }

  // The key salt is generated here so that the same password produces the same key on every
  // device the account is used from -- that is what makes a reinstall able to decrypt.
  const keySalt = randomHex(16);
  const at = nowMs();

  await env.DB.prepare(
    "INSERT INTO users (email, verifier, key_salt, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)"
  )
    .bind(email, verifier, keySalt, at, at)
    .run();

  return json({ ok: true, existing: false, keySalt });
}

/** Confirms the password and returns the salt needed to rebuild the encryption key. */
async function handleLogin(request, env) {
  const body = await readBody(request);
  const email = cleanEmail(body?.email);

  if (!emailLooksReal(email)) return bad("That does not look like an email address.");

  const row = await env.DB.prepare(
    "SELECT email, verifier, key_salt FROM users WHERE email = ?"
  )
    .bind(email)
    .first();

  // The same message either way, so this endpoint cannot be used to find out which addresses
  // have accounts.
  if (!row || !safeEqual(row.verifier, body?.verifier)) {
    return bad("Email or password is wrong.", 401);
  }

  await env.DB.prepare("UPDATE users SET last_seen_at = ? WHERE email = ?")
    .bind(nowMs(), email)
    .run();

  return json({ ok: true, keySalt: row.key_salt });
}

/**
 * Stores one or more encrypted collections.
 *
 * A write is refused if the client's revision is behind what is already stored, so a phone
 * that has been offline cannot overwrite work done somewhere newer without being told first.
 */
async function handlePush(request, env) {
  const body = await readBody(request);
  const user = await authenticate(env, body);
  if (!user) return bad("Email or password is wrong.", 401);

  const items = Array.isArray(body?.items) ? body.items : [];
  if (items.length === 0) return bad("Nothing to push.");
  if (items.length > 32) return bad("Too many collections in one push.");

  const at = nowMs();
  const written = [];
  const conflicts = [];

  for (const item of items) {
    const collection = item?.collection;
    if (!COLLECTIONS.has(collection)) return bad(`Unknown collection: ${collection}`);
    if (typeof item.ciphertext !== "string" || typeof item.iv !== "string") {
      return bad(`Malformed payload for ${collection}`);
    }
    // 4 MB of base64 per collection is far past anything this app produces, and keeps a bad
    // client from filling the database.
    if (item.ciphertext.length > 4_000_000) return bad(`Payload too large: ${collection}`);

    const revision = Number.isInteger(item.revision) ? item.revision : 0;

    const current = await env.DB.prepare(
      "SELECT revision FROM blobs WHERE email = ? AND collection = ?"
    )
      .bind(user.email, collection)
      .first();

    if (current && current.revision > revision) {
      conflicts.push({ collection, serverRevision: current.revision });
      continue;
    }

    await env.DB.prepare(
      "INSERT INTO blobs (email, collection, ciphertext, iv, revision, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT(email, collection) DO UPDATE SET " +
        "ciphertext = excluded.ciphertext, iv = excluded.iv, " +
        "revision = excluded.revision, updated_at = excluded.updated_at"
    )
      .bind(user.email, collection, item.ciphertext, item.iv, revision, at)
      .run();

    written.push(collection);
  }

  return json({ ok: true, written, conflicts, at });
}

/** Hands back every stored blob, still encrypted. */
async function handlePull(request, env) {
  const body = await readBody(request);
  const user = await authenticate(env, body);
  if (!user) return bad("Email or password is wrong.", 401);

  const { results } = await env.DB.prepare(
    "SELECT collection, ciphertext, iv, revision, updated_at FROM blobs WHERE email = ?"
  )
    .bind(user.email)
    .all();

  return json({ ok: true, items: results ?? [], keySalt: user.key_salt });
}

/** Cheap liveness check, used by the app before it bothers to encrypt anything. */
function handleHealth() {
  return json({ ok: true, service: "focus-sync", at: nowMs() });
}

// ---------------------------------------------------------------- routing

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS });
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    try {
      if (path === "/" || path === "/health") return handleHealth();

      if (request.method !== "POST") return bad("Use POST.", 405);

      switch (path) {
        case "/register":
          return await handleRegister(request, env);
        case "/login":
          return await handleLogin(request, env);
        case "/push":
          return await handlePush(request, env);
        case "/pull":
          return await handlePull(request, env);
        case "/face/upload":
          return await handleFaceUpload(request, env);
        case "/face/download":
          return await handleFaceDownload(request, env);
        default:
          return bad("No such endpoint.", 404);
      }
    } catch (err) {
      // The message is deliberately not returned: it can name columns and constraints.
      console.error(err);
      return bad("Something went wrong on the server.", 500);
    }
  },
};
