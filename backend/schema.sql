-- Focus sync store.
--
-- The server is a filing cabinet with no key to any of the drawers. Every row of user data is
-- an opaque blob encrypted on the phone with a key derived from the account password, which
-- never leaves the device and is never sent here. That is deliberate: it means no one holding
-- this database -- including whoever runs it -- can read a habit, a goal, a link or an app
-- name out of it.

CREATE TABLE IF NOT EXISTS users (
  email            TEXT PRIMARY KEY,
  -- Proves the password without revealing it or the encryption key: a second, differently
  -- salted hash of the same password. Knowing this value does not help decrypt anything.
  verifier         TEXT NOT NULL,
  -- Random per account, sent to the phone so it can re-derive the same key on a new install.
  key_salt         TEXT NOT NULL,
  created_at       INTEGER NOT NULL,
  last_seen_at     INTEGER NOT NULL
);

-- One row per (user, collection). The whole collection travels as a single ciphertext, which
-- keeps the server from learning even how many habits or goals someone has.
CREATE TABLE IF NOT EXISTS blobs (
  email            TEXT NOT NULL,
  collection       TEXT NOT NULL,
  ciphertext       TEXT NOT NULL,
  iv               TEXT NOT NULL,
  -- Bumped by the client on every write, so a stale device cannot clobber a newer copy.
  revision         INTEGER NOT NULL DEFAULT 0,
  updated_at       INTEGER NOT NULL,
  PRIMARY KEY (email, collection),
  FOREIGN KEY (email) REFERENCES users(email) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_blobs_user ON blobs(email, updated_at);
