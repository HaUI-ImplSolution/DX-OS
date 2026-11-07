// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Store } from '../dist/storage/store.js';
import { Records } from '../dist/tools/records.js';
import { ApiError } from '../dist/errors.js';

const owner = { issuer: 'https://identity.invalid', subject: 'A' };
const target = { model: 'demo', entity: 'request', id: 1 };
function setup(t, write) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-proposal-test-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  t.after(() => { store.close(); rmSync(dir, { recursive: true, force: true }); });
  const core = { read: async (_owner, path) => path === '/meta/models/demo/entities/request'
    ? { name: 'request', label: 'Demo', modelVersion: 1, canRead: true, fields: [],
      process: { actions: [{ name: 'start', label: 'Start', from: ['submitted'], to: 'working' }], effects: {} } }
    : { version: 0, status: 'submitted' }, write };
  return { store, records: new Records(core, store) };
}
test('a proposed action sends the confirmed record/model versions once', async t => {
  let calls = 0;
  const { records, store } = setup(t, async (_owner, path, body) => {
    calls++; assert.equal(path, '/data/demo/request/1/actions/start');
    assert.equal(body.version, 0); assert.equal(body.modelVersion, 1);
    return { version: 1, status: 'working' };
  });
  const p = await records.propose(owner, target, { operation: 'action', action: 'start' });
  await records.execute(owner, p.proposalId, 'web');
  await assert.rejects(records.execute(owner, p.proposalId, 'web'));
  assert.equal(calls, 1); assert.equal(store.proposal(owner, p.proposalId).state, 'succeeded');
});
test('a Core version conflict is preserved and not retried', async t => {
  let calls = 0;
  const { records, store } = setup(t, async () => { calls++; throw new ApiError(409, 'ERR_INVALID_STATE', 'Changed', 'MODEL_VERSION_CHANGED'); });
  const p = await records.propose(owner, target, { operation: 'action', action: 'start' });
  await assert.rejects(records.execute(owner, p.proposalId, 'web'), { reason: 'MODEL_VERSION_CHANGED' });
  assert.equal(calls, 1); assert.equal(store.proposal(owner, p.proposalId).state, 'failed');
});
test('an unknown write result stays uncertain and cannot be executed again', async t => {
  let calls = 0;
  const { records, store } = setup(t, async () => { calls++; throw new ApiError(503, 'ERR_EXECUTION_UNCERTAIN', 'Unknown'); });
  const p = await records.propose(owner, target, { operation: 'action', action: 'start' });
  await assert.rejects(records.execute(owner, p.proposalId, 'web'));
  await assert.rejects(records.execute(owner, p.proposalId, 'web'));
  assert.equal(calls, 1); assert.equal(store.proposal(owner, p.proposalId).state, 'uncertain');
});
