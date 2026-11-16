// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { Store } from '../dist/storage/store.js';
const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
const snapshot = { model: 'demo', entity: 'request', recordId: 1, operation: 'field', field: 'category',
  value: 'network', task: 'classify', taskRevision: 'synthetic', version: 0, modelVersion: 1, summary: 'Demo', webOnly: true };
function setup(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-store-')), key = randomBytes(32), path = join(dir, 'private/state.sqlite');
  const store = new Store(path, key);
  t.after(() => { try { store.close(); } catch {} rmSync(dir, { recursive: true, force: true }); });
  return { dir, key, path, store };
}
test('encrypted secrets and proposals are bound to issuer, subject and purpose', t => {
  const { store, path, key } = setup(t);
  store.setSecret(a, 'core', 'synthetic-secret');
  const id = store.createProposal(a, snapshot);
  assert.throws(() => store.proposal(b, id), { status: 404 });
  assert.equal(store.getSecret(b, 'core'), undefined); store.close();
  assert.equal(readFileSync(path).includes(Buffer.from('synthetic-secret')), false);
  const db = new DatabaseSync(path), ciphertext = db.prepare("SELECT value FROM secrets WHERE name='core'").get().value;
  db.prepare('INSERT INTO secrets(owner,name,value) VALUES(?,?,?)').run(JSON.stringify([b.issuer,b.subject]), 'core', ciphertext);
  db.close(); const reopened = new Store(path, key);
  try { assert.throws(() => reopened.getSecret(b, 'core')); } finally { reopened.close(); }
});
test('one claim, expiry and web confirmation remain mandatory', t => {
  const { store } = setup(t), id = store.createProposal(a, snapshot);
  assert.throws(() => store.claim(a, id, 'telegram'), { errorCode: 'ERR_WEB_CONFIRMATION_REQUIRED' });
  store.claim(a, id, 'web'); assert.throws(() => store.claim(a, id, 'web'), { status: 409 });
  const expired = store.createProposal(a, snapshot, 0);
  assert.throws(() => store.claim(a, expired, 'web', 120000), { status: 409 });
});
test('an interrupted execution remains uncertain after restart and cannot be claimed again', t => {
  const { store, key, path } = setup(t), id = store.createProposal(a, snapshot);
  store.claim(a, id, 'web'); store.close();
  const restarted = new Store(path, key);
  try {
    assert.equal(restarted.proposal(a, id).state, 'uncertain');
    assert.throws(() => restarted.claim(a, id, 'web'), { status: 409 });
  } finally { restarted.close(); }
});
test('OAuth state belongs to one browser and can only be consumed once before expiry', t => {
  const { store } = setup(t);
  store.createFlow(a, 'synthetic-state', 'synthetic-cookie', 'synthetic-verifier');
  assert.throws(() => store.takeFlow('synthetic-state', 'wrong-cookie'), { errorCode: 'ERR_LINK_INVALID' });
  assert.equal(store.takeFlow('synthetic-state', 'synthetic-cookie').owner.subject, 'A');
  assert.throws(() => store.takeFlow('synthetic-state', 'synthetic-cookie'), { errorCode: 'ERR_LINK_EXPIRED' });
  store.createFlow(a, 'expired-state', 'cookie', 'verifier', 0);
  assert.throws(() => store.takeFlow('expired-state', 'cookie', 300000), { errorCode: 'ERR_LINK_EXPIRED' });
});
test('upgrading legacy state preserves encrypted credentials, tables and history without activation', t => {
  const { store, path, key } = setup(t);
  store.setSecret(a, 'providers', { apiKey: 'synthetic-old-key' }); store.close();
  const db = new DatabaseSync(path);
  db.exec("CREATE TABLE runtime_owners(slot TEXT PRIMARY KEY,owner TEXT); INSERT INTO runtime_owners VALUES('legacy','owner'); PRAGMA user_version=4;"); db.close();
  const migrated = new Store(path, key);
  try { assert.equal(migrated.getSecret(a, 'providers').apiKey, 'synthetic-old-key'); } finally { migrated.close(); }
  const check = new DatabaseSync(path);
  assert.equal(check.prepare('SELECT slot FROM runtime_owners').get().slot, 'legacy');
  assert.equal(check.prepare('PRAGMA user_version').get().user_version, 5);
  check.exec('PRAGMA user_version=99'); check.close();
  assert.throws(() => new Store(path, key), /newer application/);
});
test('bootstrap preserves master key and legacy runtime files, and refuses a lost key', t => {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-bootstrap-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const prepare = () => spawnSync(process.execPath, [fileURLToPath(new URL('../dist/setup.js', import.meta.url)), 'prepare', dir],
    { env: { ...process.env, ASSISTANT_PUBLIC_URL: 'http://localhost:9080' }, encoding: 'utf8' });
  assert.equal(prepare().status, 0);
  const key = readFileSync(join(dir, 'private/master.key'));
  mkdirSync(join(dir, 'runtime-1'), { recursive: true }); writeFileSync(join(dir, 'runtime-1/transcript'), 'legacy');
  const store = new Store(join(dir, 'control/assistant.sqlite'), key); store.setSecret(a, 'core', 'synthetic'); store.close();
  assert.equal(prepare().status, 0); assert.deepEqual(readFileSync(join(dir, 'private/master.key')), key);
  assert.equal(readFileSync(join(dir, 'runtime-1/transcript'), 'utf8'), 'legacy');
  rmSync(join(dir, 'private/master.key')); assert.notEqual(prepare().status, 0);
});
