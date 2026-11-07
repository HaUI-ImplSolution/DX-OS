// SPDX-License-Identifier: Apache-2.0
import { createCipheriv, createDecipheriv, randomBytes, randomUUID } from 'node:crypto';
import { chmodSync, constants, mkdirSync, openSync, closeSync, lstatSync } from 'node:fs';
import { dirname } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { ApiError } from '../errors.js';

export type Owner = { issuer: string; subject: string };
export type Proposal = {
  model: string; entity: string; recordId: number; operation: 'action' | 'assign';
  action?: string; assignee?: string | null; note?: string;
  version: number; modelVersion: number; summary: string; webOnly: boolean;
  effects?: { operation: string; userSource: string; group: string; asynchronous: boolean }[];
};

const ownerKey = (owner: Owner) => JSON.stringify([owner.issuer, owner.subject]);

export class Store {
  private readonly db: DatabaseSync;
  private readonly key: Buffer;

  constructor(path: string, key: Buffer) {
    if (key.length !== 32) throw new Error('The master key must contain 32 bytes');
    this.key = Buffer.from(key);
    mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
    const parent = lstatSync(dirname(path));
    if (parent.isSymbolicLink() || !parent.isDirectory() || (parent.mode & 0o077) !== 0)
      throw new Error('Use a private state directory with mode 0700');
    closeSync(openSync(path, constants.O_CREAT | constants.O_RDWR | constants.O_NOFOLLOW, 0o600));
    chmodSync(path, 0o600);
    this.db = new DatabaseSync(path);
    this.db.exec(`PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;`);
    const version = Number((this.db.prepare('PRAGMA user_version').get() as { user_version: number }).user_version);
    if (version > 4) { this.db.close(); throw new Error('The assistant database needs a newer application'); }
    if (version === 0) this.db.exec(`BEGIN IMMEDIATE;
      CREATE TABLE secrets (owner TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL,
        PRIMARY KEY(owner,name));
      CREATE TABLE proposals (id TEXT PRIMARY KEY, owner TEXT NOT NULL, snapshot TEXT NOT NULL,
        expires INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN
        ('pending','executing','succeeded','failed','uncertain','cancelled','expired')),
        receipt TEXT, created INTEGER NOT NULL);
      CREATE INDEX proposals_owner ON proposals(owner,created);
      PRAGMA user_version=1; COMMIT;`);
    if (version < 2) this.db.exec(`BEGIN IMMEDIATE;
      CREATE TABLE oauth_flows (state TEXT PRIMARY KEY, owner TEXT NOT NULL,
        value TEXT NOT NULL, expires INTEGER NOT NULL);
      PRAGMA user_version=2; COMMIT;`);
    if (version < 3) this.db.exec(`BEGIN IMMEDIATE;
      CREATE TABLE telegram_bots (owner TEXT PRIMARY KEY, bot_id TEXT NOT NULL UNIQUE);
      PRAGMA user_version=3; COMMIT;`);
    if (version < 4) this.db.exec(`BEGIN IMMEDIATE;
      CREATE TABLE runtime_owners (slot TEXT PRIMARY KEY, owner TEXT NOT NULL UNIQUE);
      PRAGMA user_version=4; COMMIT;`);
    this.db.prepare("UPDATE proposals SET state='uncertain' WHERE state='executing'").run();
  }

  private seal(owner: Owner, name: string, data: unknown): string {
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', this.key, iv);
    cipher.setAAD(Buffer.from(JSON.stringify([owner.issuer, owner.subject, name])));
    const value = Buffer.concat([cipher.update(JSON.stringify(data), 'utf8'), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), value]).toString('base64url');
  }

  private unseal<T>(owner: Owner, name: string, encoded: string): T {
    const value = Buffer.from(encoded, 'base64url');
    const cipher = createDecipheriv('aes-256-gcm', this.key, value.subarray(0, 12));
    cipher.setAAD(Buffer.from(JSON.stringify([owner.issuer, owner.subject, name])));
    cipher.setAuthTag(value.subarray(12, 28));
    return JSON.parse(Buffer.concat([cipher.update(value.subarray(28)), cipher.final()]).toString('utf8')) as T;
  }

  getSecret<T>(owner: Owner, name: string): T | undefined {
    const row = this.db.prepare('SELECT value FROM secrets WHERE owner=? AND name=?')
      .get(ownerKey(owner), name) as { value: string } | undefined;
    return row ? this.unseal<T>(owner, name, row.value) : undefined;
  }

  setSecret(owner: Owner, name: string, value: unknown): void {
    this.db.prepare(`INSERT INTO secrets(owner,name,value) VALUES(?,?,?)
      ON CONFLICT(owner,name) DO UPDATE SET value=excluded.value`)
      .run(ownerKey(owner), name, this.seal(owner, name, value));
  }

  deleteSecret(owner: Owner, name: string): void {
    this.db.prepare('DELETE FROM secrets WHERE owner=? AND name=?').run(ownerKey(owner), name);
  }

  bindTelegram(owner: Owner, botId: string, value: unknown): void {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const existing = this.db.prepare('SELECT owner FROM telegram_bots WHERE bot_id=?').get(botId) as { owner: string } | undefined;
      if (existing && existing.owner !== ownerKey(owner))
        throw new ApiError(409, 'ERR_BOT_IN_USE', 'This bot is already linked to another account');
      this.db.prepare(`INSERT INTO telegram_bots(owner,bot_id) VALUES(?,?)
        ON CONFLICT(owner) DO UPDATE SET bot_id=excluded.bot_id`).run(ownerKey(owner), botId);
      this.setSecret(owner, 'telegram', value);
      this.deleteSecret(owner, 'notifications');
      this.db.exec('COMMIT');
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
  }

  unbindTelegram(owner: Owner): void {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      this.db.prepare('DELETE FROM telegram_bots WHERE owner=?').run(ownerKey(owner));
      this.db.prepare("DELETE FROM secrets WHERE owner=? AND name IN ('telegram','notifications')").run(ownerKey(owner));
      this.db.exec('COMMIT');
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
  }

  runtimeOwner(slot: string): Owner | undefined {
    const row = this.db.prepare('SELECT owner FROM runtime_owners WHERE slot=?').get(slot) as { owner: string } | undefined;
    if (!row) return undefined;
    const [issuer, subject] = JSON.parse(row.owner) as [string, string];
    return { issuer, subject };
  }

  runtimeSlot(owner: Owner): string | undefined {
    return (this.db.prepare('SELECT slot FROM runtime_owners WHERE owner=?').get(ownerKey(owner)) as { slot: string } | undefined)?.slot;
  }

  allocateRuntime(owner: Owner, slots: readonly string[]): string {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      let slot = this.runtimeSlot(owner);
      if (slot && !slots.includes(slot)) throw new ApiError(409, 'ERR_RUNTIME_REQUIRED', 'The assigned runtime is not configured');
      if (!slot) {
        slot = slots.find(name => !this.runtimeOwner(name));
        if (!slot) throw new ApiError(409, 'ERR_RUNTIME_CAPACITY', 'All private runtime slots are assigned');
        this.db.prepare('INSERT INTO runtime_owners(slot,owner) VALUES(?,?)').run(slot, ownerKey(owner));
      }
      this.db.exec('COMMIT'); return slot;
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
  }

  createFlow(owner: Owner, state: string, cookie: string, verifier: string, now = Date.now()): void {
    this.db.prepare('DELETE FROM oauth_flows WHERE expires<=?').run(now);
    this.db.prepare('DELETE FROM oauth_flows WHERE owner=?').run(ownerKey(owner));
    this.db.prepare('INSERT INTO oauth_flows(state,owner,value,expires) VALUES(?,?,?,?)')
      .run(state, ownerKey(owner), this.seal(owner, state, { cookie, verifier }), now + 300000);
  }

  takeFlow(state: string, cookie: string, now = Date.now()): { owner: Owner; verifier: string } {
    const row = this.db.prepare('SELECT owner,value FROM oauth_flows WHERE state=? AND expires>?')
      .get(state, now) as { owner: string; value: string } | undefined;
    if (!row) throw new ApiError(400, 'ERR_LINK_EXPIRED', 'The sign-in link has expired');
    const [issuer, subject] = JSON.parse(row.owner) as [string, string];
    const owner = { issuer, subject };
    const flow = this.unseal<{ cookie: string; verifier: string }>(owner, state, row.value);
    if (cookie !== flow.cookie) throw new ApiError(400, 'ERR_LINK_INVALID', 'The sign-in link belongs to another browser');
    const removed = this.db.prepare('DELETE FROM oauth_flows WHERE state=? AND expires>?').run(state, now);
    if (removed.changes !== 1) throw new ApiError(400, 'ERR_LINK_EXPIRED', 'The sign-in link has expired');
    return { owner, verifier: flow.verifier };
  }

  createProposal(owner: Owner, snapshot: Proposal, now = Date.now()): string {
    const count = this.db.prepare("SELECT count(*) AS count FROM proposals WHERE owner=? AND state='pending' AND expires>?")
      .get(ownerKey(owner), now) as { count: number };
    if (count.count >= 20) throw new ApiError(429, 'ERR_PROPOSAL_LIMIT', 'Handle existing proposals before creating more');
    const id = randomUUID();
    this.db.prepare(`INSERT INTO proposals(id,owner,snapshot,expires,state,created)
      VALUES(?,?,?,?,'pending',?)`).run(id, ownerKey(owner), this.seal(owner, id, snapshot), now + 120000, now);
    return id;
  }

  proposal(owner: Owner, id: string) {
    const row = this.db.prepare('SELECT snapshot,expires,state,receipt FROM proposals WHERE id=? AND owner=?')
      .get(id, ownerKey(owner)) as { snapshot: string; expires: number; state: string; receipt: string | null } | undefined;
    if (!row) throw new ApiError(404, 'ERR_NOT_FOUND', 'Proposal not found');
    return { id, snapshot: this.unseal<Proposal>(owner, id, row.snapshot), expires: row.expires,
      state: row.state, receipt: row.receipt ? this.unseal<unknown>(owner, `${id}:receipt`, row.receipt) : null };
  }

  proposals(owner: Owner, page: number, size: number) {
    const rows = this.db.prepare('SELECT id FROM proposals WHERE owner=? ORDER BY created DESC,id LIMIT ? OFFSET ?')
      .all(ownerKey(owner), size, page * size) as { id: string }[];
    return rows.map(row => this.proposal(owner, row.id));
  }

  claim(owner: Owner, id: string, channel: 'web' | 'telegram', now = Date.now()): Proposal {
    // The conditional update is the claim; reading a snapshot alone never authorizes execution.
    const proposal = this.proposal(owner, id);
    if (proposal.snapshot.webOnly && channel !== 'web')
      throw new ApiError(409, 'ERR_WEB_CONFIRMATION_REQUIRED', 'Confirm this proposal on the web');
    const result = this.db.prepare(`UPDATE proposals SET state='executing'
      WHERE id=? AND owner=? AND state='pending' AND expires>?`)
      .run(id, ownerKey(owner), now);
    if (result.changes !== 1) throw new ApiError(409, 'ERR_PROPOSAL_CLOSED', 'Proposal is expired or already handled');
    return proposal.snapshot;
  }

  cancel(owner: Owner, id: string): void {
    const result = this.db.prepare("UPDATE proposals SET state='cancelled' WHERE id=? AND owner=? AND state='pending'")
      .run(id, ownerKey(owner));
    if (result.changes !== 1) throw new ApiError(409, 'ERR_PROPOSAL_CLOSED', 'Proposal is already handled');
  }

  finish(owner: Owner, id: string, state: 'succeeded' | 'failed' | 'uncertain', receipt: unknown): void {
    const result = this.db.prepare("UPDATE proposals SET state=?,receipt=? WHERE id=? AND owner=? AND state='executing'")
      .run(state, this.seal(owner, `${id}:receipt`, receipt), id, ownerKey(owner));
    if (result.changes !== 1) throw new ApiError(409, 'ERR_PROPOSAL_CLOSED', 'Proposal is not executing');
  }

  close(): void { this.db.close(); this.key.fill(0); }
}
