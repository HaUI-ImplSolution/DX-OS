// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, readFileSync, cpSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Store } from '../dist/storage/store.js';

const a = { issuer: 'https://identity.invalid', subject: 'A' };
const b = { ...a, subject: 'B' };
const snapshot = { model: 'demo', entity: 'request', recordId: 1, operation: 'action', action: 'start',
  version: 0, modelVersion: 1, summary: 'Demo', webOnly: false };
function setup(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-store-test-'));
  const key = randomBytes(32), path = join(dir, 'private/state.sqlite');
  const store = new Store(path, key);
  t.after(() => { try { store.close(); } catch {} rmSync(dir, { recursive: true, force: true }); key.fill(0); });
  return { dir, path, key, store };
}

test('owner binding isolates encrypted secrets and proposals', t => {
  const { store, path } = setup(t);
  store.setSecret(a, 'provider', 'synthetic-secret');
  assert.equal(store.getSecret(b, 'provider'), undefined);
  const id = store.createProposal(a, snapshot);
  assert.throws(() => store.proposal(b, id), { status: 404 });
  store.close();
  assert.equal(readFileSync(path).includes(Buffer.from('synthetic-secret')), false);
});
test('only one confirmation can claim a proposal', t => {
  const { store } = setup(t), id = store.createProposal(a, snapshot);
  store.claim(a, id, 'web');
  assert.throws(() => store.claim(a, id, 'telegram'), { status: 409 });
});
test('sensitive effects cannot be confirmed from Telegram', t => {
  const { store } = setup(t), id = store.createProposal(a, { ...snapshot, webOnly: true });
  assert.throws(() => store.claim(a, id, 'telegram'), { errorCode: 'ERR_WEB_CONFIRMATION_REQUIRED' });
  assert.equal(store.proposal(a, id).state, 'pending');
});
test('restart keeps an interrupted execution uncertain, never pending', t => {
  const { store, path, key } = setup(t), id = store.createProposal(a, snapshot);
  store.claim(a, id, 'web'); store.close();
  const restored = new Store(path, key);
  try {
    assert.equal(restored.proposal(a, id).state, 'uncertain');
    assert.throws(() => restored.claim(a, id, 'web'), { status: 409 });
  } finally { restored.close(); }
});
test('bot ownership is unique and a refused binding preserves the old one', t => {
  const { store } = setup(t);
  store.bindTelegram(a, '100', { username: 'first' });
  store.bindTelegram(b, '200', { username: 'second' });
  assert.throws(() => store.bindTelegram(b, '100', {}), { errorCode: 'ERR_BOT_IN_USE' });
  assert.equal(store.getSecret(b, 'telegram').username, 'second');
});
test('OAuth links are bound to a browser and consumed once', t => {
  const { store } = setup(t);
  store.createFlow(a, 'synthetic-state', 'synthetic-cookie', 'synthetic-verifier');
  assert.throws(() => store.takeFlow('synthetic-state', 'other-browser'));
  assert.equal(store.takeFlow('synthetic-state', 'synthetic-cookie').owner.subject, 'A');
  assert.throws(() => store.takeFlow('synthetic-state', 'synthetic-cookie'));
});
test('a closed database and its separate key restore encrypted state', t => {
  const { store, dir, key } = setup(t);
  store.setSecret(a, 'provider', { model: 'synthetic' }); store.close();
  cpSync(join(dir, 'private'), join(dir, 'backup'), { recursive: true });
  chmodSync(join(dir, 'backup'), 0o700);
  const restored = new Store(join(dir, 'backup/state.sqlite'), key);
  try { assert.equal(restored.getSecret(a, 'provider').model, 'synthetic'); }
  finally { restored.close(); }
});

test('runtime ownership survives restart and a full pool never reassigns a slot', t => {
  const { store, path, key } = setup(t);
  assert.equal(store.allocateRuntime(a, ['one', 'two']), 'one');
  assert.equal(store.allocateRuntime(b, ['one', 'two']), 'two');
  const c = { ...a, subject: 'C' };
  assert.throws(() => store.allocateRuntime(c, ['one', 'two']), { errorCode: 'ERR_RUNTIME_CAPACITY' });
  store.close();
  const restored = new Store(path, key);
  try {
    assert.equal(restored.allocateRuntime(a, ['one', 'two']), 'one');
    assert.equal(restored.runtimeOwner('two').subject, 'B');
    assert.throws(() => restored.allocateRuntime(c, ['one', 'two']), { errorCode: 'ERR_RUNTIME_CAPACITY' });
  } finally { restored.close(); }
});

test('unlink releases only the owner bot after removing its Telegram state', t => {
  const { store } = setup(t);
  store.bindTelegram(a, '100', { token: 'synthetic-one' });
  store.bindTelegram(b, '200', { token: 'synthetic-two' });
  store.setSecret(b, 'notifications', { enabled: true });
  store.unbindTelegram(b);
  assert.equal(store.getSecret(b, 'telegram'), undefined);
  assert.equal(store.getSecret(b, 'notifications'), undefined);
  assert.equal(store.getSecret(a, 'telegram').token, 'synthetic-one');
  const c = { ...a, subject: 'C' };
  store.bindTelegram(c, '200', { token: 'synthetic-two' });
  assert.throws(() => store.bindTelegram(c, '100', {}), { errorCode: 'ERR_BOT_IN_USE' });
});
