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
  const entity = { name: 'request', label: 'Demo', modelVersion: 1, canRead: true, fields: [],
      process: { actions: [{ name: 'start', label: 'Start', from: ['submitted'], to: 'working' }], effects: {} } };
  const core = { read: async (_owner, path) => path === '/meta/models/demo/entities/request'
    ? entity : { version: 0, status: 'submitted' }, write };
  return { store, entity, records: new Records(core, store) };
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

test('a handler cannot propose an action excluded by Core metadata', async t => {
  const { records, entity } = setup(t, () => assert.fail('must not write'));
  entity.handlerActions = [];
  entity.process.actions[0].by = 'HANDLER';
  await assert.rejects(records.propose(owner, target, { operation: 'action', action: 'start' }),
    { status: 403, errorCode: 'ERR_ACTION_NOT_ALLOWED' });
});

test('an older Core without model versions disables assistant writes', async t => {
  const { records, entity, store } = setup(t, () => assert.fail('must not write'));
  delete entity.modelVersion;
  await assert.rejects(records.propose(owner, target, { operation: 'action', action: 'start' }),
    { errorCode: 'ERR_CONDITIONAL_API_REQUIRED' });
  assert.deepEqual(store.proposals(owner, 0, 5), []);
});

test('unknown or membership effects require web confirmation', async t => {
  const { records, entity, store } = setup(t, async () => ({ status: 'working' }));
  for (const effects of [undefined, { working: [{ operation: 'add_to_group', user: 'creator', group: 'synthetic', asynchronous: true }] }]) {
    entity.process.effects = effects;
    const proposal = await records.propose(owner, target, { operation: 'action', action: 'start' });
    assert.equal(proposal.webOnly, true);
    await assert.rejects(records.execute(owner, proposal.proposalId, 'telegram'), { errorCode: 'ERR_WEB_CONFIRMATION_REQUIRED' });
    assert.equal(store.proposal(owner, proposal.proposalId).state, 'pending');
    await records.execute(owner, proposal.proposalId, 'web');
  }
});

test('a pending proposal cannot execute after Core loses conditional write support', async t => {
  const { records, entity, store } = setup(t, () => assert.fail('must not write to an older Core'));
  const p = await records.propose(owner, target, { operation: 'action', action: 'start' });
  delete entity.modelVersion;
  for (const channel of ['web', 'telegram']) {
    await assert.rejects(records.execute(owner, p.proposalId, channel), { errorCode: 'ERR_CONDITIONAL_API_REQUIRED' });
    assert.equal(store.proposal(owner, p.proposalId).state, 'pending');
  }
});

test('legacy snapshots missing a version cannot trigger unconditional Core writes', async t => {
  const { records, store } = setup(t, () => assert.fail('must not write without both preconditions'));
  const proposed = await records.propose(owner, target, { operation: 'action', action: 'start' });
  const snapshot = store.proposal(owner, proposed.proposalId).snapshot;
  for (const field of ['version', 'modelVersion']) {
    const legacy = { ...snapshot }; delete legacy[field];
    const id = store.createProposal(owner, legacy);
    await assert.rejects(records.execute(owner, id, 'web'), { errorCode: 'ERR_PROPOSAL_VERSION_REQUIRED' });
    assert.equal(store.proposal(owner, id).state, 'pending');
  }
});
