// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, readFileSync } from 'node:fs';
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
test('a proposal can be claimed once and not after it expires', t => {
  const { store } = setup(t), id = store.createProposal(a, snapshot);
  store.claim(a, id); assert.throws(() => store.claim(a, id), { status: 409 });
  const expired = store.createProposal(a, snapshot, 0);
  assert.throws(() => store.claim(a, expired, 120000), { status: 409 });
});
test('an interrupted execution remains uncertain after restart and cannot be claimed again', t => {
  const { store, key, path } = setup(t), id = store.createProposal(a, snapshot);
  store.claim(a, id); store.close();
  const restarted = new Store(path, key);
  try {
    assert.equal(restarted.proposal(a, id).state, 'uncertain');
    assert.throws(() => restarted.claim(a, id), { status: 409 });
  } finally { restarted.close(); }
});
test('OAuth state belongs to one browser and can be consumed only once', t => {
  const { store } = setup(t);
  store.createFlow(a, 'synthetic-state', 'synthetic-cookie', 'synthetic-verifier');
  assert.throws(() => store.takeFlow('synthetic-state', 'wrong-cookie'), { errorCode: 'ERR_LINK_INVALID' });
  assert.equal(store.takeFlow('synthetic-state', 'synthetic-cookie').owner.subject, 'A');
  assert.throws(() => store.takeFlow('synthetic-state', 'synthetic-cookie'), { errorCode: 'ERR_LINK_EXPIRED' });
});
test('bootstrap keeps the master key of existing state and refuses to make a new one', t => {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-bootstrap-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const prepare = () => spawnSync(process.execPath, [fileURLToPath(new URL('../dist/setup.js', import.meta.url)), 'prepare', dir],
    { env: { ...process.env, ASSISTANT_PUBLIC_URL: 'http://localhost:9080' }, encoding: 'utf8' });
  assert.equal(prepare().status, 0);
  const key = readFileSync(join(dir, 'private/master.key'));
  const store = new Store(join(dir, 'control/assistant.sqlite'), key); store.setSecret(a, 'core', 'synthetic'); store.close();
  assert.equal(prepare().status, 0); assert.deepEqual(readFileSync(join(dir, 'private/master.key')), key);
  rmSync(join(dir, 'private/master.key')); assert.notEqual(prepare().status, 0);
});

test('a wrong master key is refused and leaves the state untouched', t => {
  const { store, path } = setup(t);
  store.setSecret(a, 'core', 'synthetic');
  const id = store.createProposal(a, snapshot);
  store.claim(a, id); store.close();
  assert.throws(() => new Store(path, randomBytes(32)), /matching master key/);
  const db = new DatabaseSync(path, { readOnly: true });
  assert.equal(db.prepare('SELECT state FROM proposals WHERE id=?').get(id).state, 'executing'); db.close();
});
