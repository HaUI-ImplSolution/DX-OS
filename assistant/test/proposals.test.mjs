// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Store } from '../dist/storage/store.js';
import { Records } from '../dist/tools/records.js';
import { FieldProposals } from '../dist/tools/fields.js';
import { ApiError } from '../dist/errors.js';
import { loadClassifiers } from '../dist/classifier/index.js';
import { createServer } from '../dist/server.js';
const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
const target = { model: 'demo', entity: 'request', id: 1 };
const source = { id: 'guide_v1', title: 'Synthetic guide', revision: '1', text: 'Synthetic network incidents use network.', license: 'CC0-1.0' };
function setup(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-fields-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  let close = () => store.close();
  t.after(async () => { await close(); rmSync(dir, { recursive: true, force: true }); });
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
  return { dir, store, entity, record, core, records, fields, inference,
    useServer: app => { close = () => app.close(); },
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

const supportWeights = new URL('../../tools/classifier/model/support.json', import.meta.url);
const parityFixture = JSON.parse(readFileSync(new URL('./fixtures/classifier.json', import.meta.url), 'utf8'));

function setupClassifier(t) {
  const f = setup(t);
  const classifiers = loadClassifiers(fileURLToPath(new URL('../../tools/classifier/model/', import.meta.url)));
  const model = classifiers.get('support');
  const support = new Map([['support', model]]);
  const example = parityFixture.support.cases.find(row => row.label && row.inputs.every(Boolean));
  f.entity.fields.push({ name: 'title', label: 'Title', type: 'text' });
  f.entity.fields[1].options = [...model.labels].reverse();
  f.entity.aiTasks.local = { label: 'Classify locally', engine: 'classifier', classifier: 'support',
    inputFields: ['title', 'detail'], outputField: 'category' };
  Object.assign(f.record, { title: example.inputs[0], detail: example.inputs[1],
    category: model.labels.find(label => label !== example.label) });
  const fields = new FieldProposals(f.records, f.core, f.store, undefined, [], support);
  return { ...f, fields, example, support };
}

async function setupReviews(t) {
  const f = setupClassifier(t);
  const publicUrl = new URL('http://dxos.invalid');
  const app = await createServer(f.store, async authorization => {
    if (authorization !== 'Bearer owner') throw new ApiError(401, 'ERR_UNAUTHORIZED', 'Unauthorized');
    return { ...a, admin: false };
  }, { publicUrl, session: {}, fields: f.fields, records: f.records });
  f.useServer(app);
  const headers = { origin: publicUrl.origin, authorization: 'Bearer owner' };
  const review = items => app.inject({ method: 'POST', url: '/assistant-api/v1/me/reviews', headers, payload: { items } });
  return { ...f, app, headers, review };
}

test('batch review uses the caller and omits forbidden, missing, read-only and closed records', async t => {
  const f = await setupReviews(t);
  const read = f.core.read.bind(f.core);
  f.entity.process = { states: [{ name: 'closed', terminal: true }] };
  f.core.read = async (owner, path) => {
    assert.equal(owner.subject, a.subject);
    assert.equal(owner.issuer, a.issuer);
    if (path.endsWith('/readonly')) return { ...f.entity, canWrite: false };
    if (path.endsWith('/2')) throw new ApiError(403, 'ERR_ACTION_NOT_ALLOWED', 'Denied');
    if (path.endsWith('/3')) throw new ApiError(404, 'ERR_NOT_FOUND', 'Missing');
    if (path.endsWith('/4')) return { ...f.record, status: 'closed' };
    return read(owner, path);
  };
  const response = await f.review([target, ...[2, 3, 4].map(id => ({ ...target, id })), { ...target, entity: 'readonly' }]);
  assert.equal(response.statusCode, 200, response.body);
  assert.equal(response.headers['cache-control'], 'no-store');
  assert.deepEqual(response.json().data.items, [{ ...target, task: 'local', field: 'category', current: f.record.category,
    suggested: f.example.label, evidence: f.example.evidence, mismatch: true }]);
  assert.equal(f.writes(), 0);
  assert.equal(f.store.proposals(a, 0, 5).length, 0);
});

test('batch review accepts at most 25 records and publishes its schemas', async t => {
  const f = await setupReviews(t);
  const items = Array.from({ length: 26 }, (_, i) => ({ ...target, id: i + 1 }));
  let coreReads = 0;
  const read = f.core.read.bind(f.core);
  f.core.read = async (...args) => { coreReads++; return read(...args); };
  assert.equal((await f.review(items)).statusCode, 400);
  assert.equal(coreReads, 0);
  const accepted = await f.review(items.slice(0, 25));
  assert.equal(accepted.statusCode, 200, accepted.body);
  assert.equal(accepted.json().data.items.length, 25);
  const api = await f.app.inject({ method: 'GET', url: '/assistant-api/v1/openapi.json', headers: f.headers });
  const operation = api.json().paths['/assistant-api/v1/me/reviews'].post;
  assert.equal(operation.requestBody.content['application/json'].schema.properties.items.maxItems, 25);
  assert.equal(operation.responses[200].content['application/json'].schema.properties.data.properties.items.items.properties.suggested.nullable, true);
});

test('batch review omits incompatible tasks and reports matches and abstentions without flags', async t => {
  const f = await setupReviews(t);
  f.entity.fields.push({ name: 'other', type: 'choice', options: ['unrelated'] });
  f.entity.aiTasks.incompatible = { ...f.entity.aiTasks.local, outputField: 'other' };
  f.entity.aiTasks.unloaded = { ...f.entity.aiTasks.local, classifier: 'missing' };
  f.record.category = f.example.label;
  const match = await f.review([target]);
  assert.equal(match.statusCode, 200, match.body);
  assert.deepEqual(match.json().data.items, [{ ...target, task: 'local', field: 'category', current: f.example.label,
    suggested: f.example.label, evidence: f.example.evidence, mismatch: false }]);
  Object.assign(f.record, { title: '', detail: '' });
  delete f.record.category;
  const abstention = await f.review([target]);
  assert.equal(abstention.statusCode, 200, abstention.body);
  assert.deepEqual(abstention.json().data.items, [{ ...target, task: 'local', field: 'category', current: null,
    suggested: null, evidence: [], mismatch: false }]);
});

test('a classifier can propose and confirm through the API without LLM or a source catalog', async t => {
  const f = setupClassifier(t);
  const publicUrl = new URL('http://dxos.invalid');
  const app = await createServer(f.store, async () => ({ ...a, admin: false }),
    { publicUrl, session: {}, fields: f.fields, records: f.records });
  f.useServer(app);
  const me = await app.inject({ method: 'GET', url: '/assistant-api/v1/me' });
  assert.deepEqual(me.json().data.model, { configured: true, llmConfigured: false, classifiers: ['support'] });
  const api = await app.inject({ method: 'GET', url: '/assistant-api/v1/openapi.json' });
  assert.match(api.json().info.description, /ASSISTANT_CLASSIFIERS_DIR/);
  assert.match(api.json().paths['/assistant-api/v1/me/proposals'].post.responses[409].description, /ERR_CLASSIFIER_LABELS/);
  const headers = { origin: publicUrl.origin };
  const llm = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/proposals',
    headers, payload: { ...target, task: 'classify' } });
  assert.equal(llm.statusCode, 409, llm.body);
  assert.equal(llm.json().errorCode, 'ERR_INFERENCE_REQUIRED');
  const created = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/proposals',
    headers, payload: { ...target, task: 'local' } });
  assert.equal(created.statusCode, 200, created.body);
  const p = created.json().data.proposal;
  assert.equal(p.snapshot.value, f.example.label);
  assert.deepEqual(p.snapshot.evidence, f.example.evidence);
  assert.deepEqual(p.snapshot.sources, []);
  assert.equal(f.writes(), 0);
  const confirmed = await app.inject({ method: 'POST', url: `/assistant-api/v1/me/proposals/${p.id}/confirm`,
    headers, payload: {} });
  assert.equal(confirmed.statusCode, 200, confirmed.body);
  assert.equal(confirmed.json().data.record.category, f.example.label);
  assert.equal(f.writes(), 1);
});

test('missing or invalid weights, mismatched labels and abstentions affect only their task', async t => {
  const f = setupClassifier(t);
  await assert.rejects(f.fields.create(a, target, 'classify'), { status: 409, errorCode: 'ERR_INFERENCE_REQUIRED' });
  f.entity.aiTasks.missing = { ...f.entity.aiTasks.local, classifier: 'missing' };
  await assert.rejects(f.fields.create(a, target, 'missing'), { status: 409, errorCode: 'ERR_CLASSIFIER_UNAVAILABLE' });
  const options = f.entity.fields[1].options;
  for (const invalid of [options.slice(1), [...options, 'other'], [...options.slice(1), 'wrong']]) {
    f.entity.fields[1].options = invalid;
    await assert.rejects(f.fields.create(a, target, 'local'), { status: 409, errorCode: 'ERR_CLASSIFIER_LABELS' });
  }
  f.entity.fields[1].options = options;
  const path = join(f.dir, 'support.json');
  writeFileSync(path, readFileSync(supportWeights));
  writeFileSync(join(f.dir, 'broken.json'), '{}');
  const fromDisk = loadClassifiers(f.dir);
  assert.deepEqual([...fromDisk.keys()], ['support']);
  const fields = new FieldProposals(f.records, f.core, f.store, undefined, [], fromDisk);
  await assert.rejects(fields.create(a, target, 'missing'), { status: 409, errorCode: 'ERR_CLASSIFIER_UNAVAILABLE' });
  assert.equal((await fields.create(a, target, 'local')).proposal.snapshot.value, f.example.label);
  Object.assign(f.record, { title: '', detail: null });
  assert.deepEqual(await fields.create(a, target, 'local'), { insufficient: true, proposal: null });
  assert.equal(f.writes(), 0);
});

test('evidence from another field is rejected', async t => {
  const f = setupClassifier(t);
  f.record.private_note = 'undeclared source';
  const model = f.support.get('support');
  const stub = { labels: model.labels, revision: model.revision,
    predict: () => ({ label: f.example.label, evidence: [f.record.private_note] }) };
  const fields = new FieldProposals(f.records, f.core, f.store, undefined, [], new Map([['support', stub]]));
  await assert.rejects(fields.create(a, target, 'local'), { errorCode: 'ERR_INFERENCE_OUTPUT' });
  assert.equal(f.store.proposals(a, 0, 5).length, 0);
});

test('a different weights SHA-256 makes a classifier proposal stale on revise and confirm', async t => {
  const f = setupClassifier(t);
  const p = (await f.fields.create(a, target, 'local')).proposal;
  const path = join(f.dir, 'support.json');
  writeFileSync(path, readFileSync(supportWeights, 'utf8') + '\n');
  const replaced = loadClassifiers(f.dir);
  assert.notEqual(replaced.get('support').revision, f.support.get('support').revision);
  const fields = new FieldProposals(f.records, f.core, f.store, undefined, [], replaced);
  await assert.rejects(fields.revise(a, p.id, f.example.label), { status: 409, errorCode: 'ERR_PROPOSAL_STALE' });
  await assert.rejects(fields.execute(a, p.id), { status: 409, errorCode: 'ERR_PROPOSAL_STALE' });
  assert.equal(f.writes(), 0);
});

test('explicit LLM metadata retains the revision of a proposal created without engine metadata', async t => {
  const f = setup(t);
  const p = (await f.fields.create(a, target, 'classify')).proposal;
  Object.assign(f.entity.aiTasks.classify, { engine: 'llm', classifier: null });
  await f.fields.execute(a, p.id);
  assert.equal(f.writes(), 1);
});

