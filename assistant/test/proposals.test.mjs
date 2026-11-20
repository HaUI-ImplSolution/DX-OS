// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Store } from '../dist/storage/store.js';
import { Records } from '../dist/tools/records.js';
import { FieldProposals } from '../dist/tools/fields.js';
import { ApiError } from '../dist/errors.js';
const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
const target = { model: 'demo', entity: 'request', id: 1 };
const source = { id: 'guide_v1', title: 'Synthetic guide', revision: '1', text: 'Synthetic network incidents use network.', license: 'CC0-1.0' };
function setup(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-fields-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  t.after(() => { store.close(); rmSync(dir, { recursive: true, force: true }); });
  const entity = { name: 'request', label: 'Request', canRead: true, canWrite: true, canAssign: false,
    modelVersion: 1, fields: [{ name: 'detail', label: 'Detail', type: 'text' },
      { name: 'category', label: 'Category', type: 'choice', options: ['network','device','other'] }],
    aiTasks: { classify: { label: 'Classify', inputFields: ['detail'], outputField: 'category',
      instruction: 'Classify from source', sources: ['guide_v1'], maxLength: 100 } } };
  const record = { version: 0, status: 'open', detail: 'Wi-Fi unavailable', category: 'device' };
  let writes = 0; let failure;
  const core = {
    async read(owner, path) {
      if (owner.subject !== 'A') throw new ApiError(403, 'ERR_ACTION_NOT_ALLOWED', 'Denied');
      return path.startsWith('/meta/') ? structuredClone(entity) : structuredClone(record);
    },
    async patch(owner, path, body) {
      writes++;
      if (failure) throw failure;
      assert.equal(owner.subject, 'A'); assert.equal(path, '/data/demo/request/1');
      assert.equal(body.version, record.version); assert.equal(body.modelVersion, entity.modelVersion);
      assert.deepEqual(Object.keys(body).sort(), ['category','modelVersion','version']);
      record.category = body.category; record.version++;
      return structuredClone(record);
    },
  };
  let response = { status: 'proposed', checks: {}, value: 'network', sources: ['guide_v1'] }, infer;
  const inference = { revision: 'synthetic-model-1', async suggest(...args) {
    if (infer) return infer(...args); return structuredClone(response);
  } };
  const records = new Records(core, store);
  const fields = new FieldProposals(records, core, store, inference, [source]);
  return { store, entity, record, core, records, fields, inference,
    writes: () => writes, setFailure: value => { failure = value; }, setResponse: value => { response = value; },
    onInfer: value => { infer = value; } };
}
test('field suggestions remain private and do not write until one explicit confirmation', async t => {
  const f = setup(t), result = await f.fields.create(a, target, 'classify'), id = result.proposal.id;
  assert.equal(f.writes(), 0); assert.equal(result.proposal.snapshot.value, 'network');
  assert.deepEqual(result.proposal.snapshot.sources, [source]);
  assert.throws(() => f.store.proposal(b, id), { status: 404 });
  await assert.rejects(f.fields.execute(b, id), { status: 404 });
  const results = await Promise.allSettled([f.fields.execute(a, id), f.fields.execute(a, id)]);
  assert.equal(results.filter(x => x.status === 'fulfilled').length, 1);
  assert.equal(f.writes(), 1); assert.equal(f.store.proposal(a, id).state, 'succeeded');
});
test('record, model, task, source and model revision changes invalidate a pending suggestion', async t => {
  for (const change of ['record','model','task','source','inference']) {
    const f = setup(t), p = (await f.fields.create(a, target, 'classify')).proposal;
    let service = f.fields;
    if (change === 'record') f.record.version++;
    if (change === 'model') f.entity.modelVersion++;
    if (change === 'task') f.entity.aiTasks.classify.instruction = 'Changed';
    if (change === 'source') service = new FieldProposals(f.records, f.core, f.store, f.inference, [{ ...source, text: 'Changed' }]);
    if (change === 'inference') f.inference.revision = 'synthetic-model-2';
    await assert.rejects(service.execute(a, p.id), { errorCode: 'ERR_PROPOSAL_STALE' });
    assert.equal(f.writes(), 0);
  }
});
test('loss of access, terminal state, cancellation and expiry prevent writes', async t => {
  for (const change of ['permission','terminal','cancel','expiry']) {
    const f = setup(t), p = (await f.fields.create(a, target, 'classify')).proposal;
    if (change === 'permission') f.entity.canWrite = false;
    if (change === 'terminal') f.entity.process = { states: [{ name: 'open', terminal: true }] };
    if (change === 'cancel') f.store.cancel(a, p.id);
    if (change === 'expiry') {
      t.mock.timers.enable({ apis: ['Date'], now: p.expires + 1 });
    }
    await assert.rejects(f.fields.execute(a, p.id)); assert.equal(f.writes(), 0);
    if (change === 'expiry') t.mock.timers.reset();
  }
});
test('an edited suggestion replaces the old one and only the edit can be saved', async t => {
  const f = setup(t), p = (await f.fields.create(a, target, 'classify')).proposal;
  const revised = await f.fields.revise(a, p.id, 'other');
  assert.equal(f.store.proposal(a, p.id).state, 'cancelled');
  assert.notEqual(revised.id, p.id); assert.equal(f.writes(), 0);
  await f.fields.execute(a, revised.id); assert.equal(f.record.category, 'other');
});
test('output that is not a valid suggestion never becomes confirmable', async t => {
  const f = setup(t);
  for (const response of [null, { status: 'proposed', checks: {}, value: 'unknown', sources: ['guide_v1'] },
    { status: 'proposed', checks: {}, value: 'network', sources: ['invented'] }]) {
    f.setResponse(response);
    await assert.rejects(f.fields.create(a, target, 'classify'), { errorCode: 'ERR_INFERENCE_OUTPUT' });
  }
  assert.equal(f.store.proposals(a, 0, 5).length, 0); assert.equal(f.writes(), 0);
});
test('the model is given only the declared record fields', async t => {
  const f = setup(t); f.record.secret = 'must-not-leave-core';
  f.onInfer(async (_instruction, input) => {
    assert.deepEqual(input.record, { detail: 'Wi-Fi unavailable' });
    assert.equal(JSON.stringify(input).includes('must-not-leave-core'), false);
    return { status: 'insufficient', checks: {}, value: null, sources: ['guide_v1'] };
  });
  assert.deepEqual(await f.fields.create(a, target, 'classify'), { insufficient: true, proposal: null });
  assert.equal(f.store.proposals(a, 0, 5).length, 0);
});
test('a record changed while inference runs cannot receive its old suggestion', async t => {
  const f = setup(t); f.onInfer(async () => {
    f.record.version++; return { status: 'proposed', checks: {}, value: 'network', sources: ['guide_v1'] };
  });
  await assert.rejects(f.fields.create(a, target, 'classify'), { errorCode: 'ERR_PROPOSAL_STALE' });
  assert.equal(f.store.proposals(a, 0, 5).length, 0);
});
test('a timeout after dispatch remains uncertain and cannot be retried', async t => {
  const f = setup(t), p = (await f.fields.create(a, target, 'classify')).proposal;
  f.setFailure(new ApiError(503, 'ERR_EXECUTION_UNCERTAIN', 'Unknown result'));
  await assert.rejects(f.fields.execute(a, p.id));
  assert.equal(f.store.proposal(a, p.id).state, 'uncertain');
  await assert.rejects(f.fields.execute(a, p.id)); assert.equal(f.writes(), 1);
});
test('a Core receipt without an advanced version is uncertain rather than reported saved', async t => {
  const f = setup(t); let calls = 0;
  f.core.patch = async () => { calls++; return structuredClone(f.record); };
  const p = (await f.fields.create(a, target, 'classify')).proposal;
  await assert.rejects(f.fields.execute(a, p.id), { errorCode: 'ERR_EXECUTION_UNCERTAIN' });
  assert.equal(f.store.proposal(a, p.id).state, 'uncertain');
  await assert.rejects(f.fields.execute(a, p.id)); assert.equal(calls, 1);
});

