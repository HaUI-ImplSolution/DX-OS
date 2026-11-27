// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CoreSession } from '../dist/auth/core-session.js';
import { Store } from '../dist/storage/store.js';
import { CoreClient } from '../dist/tools/core-client.js';
import { Records } from '../dist/tools/records.js';
import { FieldProposals } from '../dist/tools/fields.js';
import { ApiError } from '../dist/errors.js';
import { loadClassifiers } from '../dist/classifier/index.js';
import { createServer } from '../dist/server.js';
const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
const target = { model: 'demo', entity: 'request', id: 1 };
const supportWeights = new URL('../../tools/classifier/model/support.json', import.meta.url);
const parityFixture = JSON.parse(readFileSync(new URL('./fixtures/classifier.json', import.meta.url), 'utf8'));
function setup(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-fields-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  let close = () => store.close();
  t.after(async () => { await close(); rmSync(dir, { recursive: true, force: true }); });
  const support = loadClassifiers(fileURLToPath(new URL('../../tools/classifier/model/', import.meta.url)));
  const model = support.get('support');
  const example = parityFixture.support.cases.find(row => row.label && row.inputs.every(Boolean));
  const confidence = model.predict(example.inputs).probability;
  const entity = { name: 'request', label: 'Request', canRead: true, canWrite: true, canAssign: false,
    modelVersion: 1, fields: [{ name: 'title', label: 'Title', type: 'text' }, { name: 'detail', label: 'Detail', type: 'text' },
      { name: 'category', label: 'Category', type: 'choice', options: [...model.labels].reverse() }],
    aiTasks: { classify: { label: 'Classify', classifier: 'support', inputFields: ['title', 'detail'], outputField: 'category' } } };
  const record = { version: 0, status: 'open', title: example.inputs[0], detail: example.inputs[1],
    category: model.labels.find(label => label !== example.label) };
  // A label that is neither the suggestion nor the current value.
  const other = model.labels.find(label => label !== example.label && label !== record.category);
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
  const records = new Records(core, store);
  const fields = new FieldProposals(records, core, store, support);
  return { dir, store, entity, record, core, records, fields, support, example, confidence, other,
    useServer: app => { close = () => app.close(); },
    writes: () => writes, setFailure: value => { failure = value; } };
}
// The loaded support classifier with its prediction replaced.
function stubbed(f, predict) {
  const model = f.support.get('support');
  return new FieldProposals(f.records, f.core, f.store,
    new Map([['support', { labels: model.labels, revision: model.revision, predict }]]));
}
test('field suggestions remain private and do not write until one explicit confirmation', async t => {
  const f = setup(t), result = await f.fields.create(a, target, 'classify'), id = result.proposal.id;
  assert.equal(f.writes(), 0); assert.equal(result.proposal.snapshot.value, f.example.label);
  assert.deepEqual(result.proposal.snapshot.evidence, f.example.evidence);
  assert.equal(result.proposal.snapshot.confidence, f.confidence);
  assert.throws(() => f.store.proposal(b, id), { status: 404 });
  await assert.rejects(f.fields.execute(b, id), { status: 404 });
  const results = await Promise.allSettled([f.fields.execute(a, id), f.fields.execute(a, id)]);
  assert.equal(results.filter(x => x.status === 'fulfilled').length, 1);
  assert.equal(f.writes(), 1); assert.equal(f.store.proposal(a, id).state, 'succeeded');
});
test('record, model, task and weights changes invalidate a pending suggestion', async t => {
  for (const change of ['record','model','task','weights']) {
    const f = setup(t), p = (await f.fields.create(a, target, 'classify')).proposal;
    let service = f.fields;
    if (change === 'record') f.record.version++;
    if (change === 'model') f.entity.modelVersion++;
    if (change === 'task') f.entity.aiTasks.classify.label = 'Changed';
    if (change === 'weights') {
      const path = join(f.dir, 'support.json');
      writeFileSync(path, readFileSync(supportWeights, 'utf8') + '\n');
      const replaced = loadClassifiers(f.dir);
      assert.notEqual(replaced.get('support').revision, f.support.get('support').revision);
      service = new FieldProposals(f.records, f.core, f.store, replaced);
      await assert.rejects(service.revise(a, p.id, f.example.label), { status: 409, errorCode: 'ERR_PROPOSAL_STALE' });
    }
    await assert.rejects(service.execute(a, p.id), { status: 409, errorCode: 'ERR_PROPOSAL_STALE' });
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
  const revised = await f.fields.revise(a, p.id, f.other);
  assert.equal(f.store.proposal(a, p.id).state, 'cancelled');
  assert.notEqual(revised.id, p.id); assert.equal(f.writes(), 0);
  assert.equal(revised.snapshot.value, f.other);
  assert.equal(revised.snapshot.confidence, undefined);
  assert.equal(f.store.proposal(a, p.id).snapshot.confidence, f.confidence);
  await f.fields.execute(a, revised.id); assert.equal(f.record.category, f.other);
});
test('a value outside the field options never becomes confirmable', async t => {
  const f = setup(t), p = (await f.fields.create(a, target, 'classify')).proposal;
  await assert.rejects(f.fields.revise(a, p.id, 'unknown'), { errorCode: 'ERR_PROPOSAL_VALUE' });
  const fields = stubbed(f, () => ({ label: 'unknown', probability: 1, evidence: f.example.evidence }));
  await assert.rejects(fields.create(a, target, 'classify'), { errorCode: 'ERR_PROPOSAL_VALUE' });
  assert.equal(f.store.proposals(a, 0, 5).length, 1); assert.equal(f.writes(), 0);
});
test('the classifier is given only the declared record fields', async t => {
  const f = setup(t); f.record.secret = 'must-not-be-read';
  let seen;
  const fields = stubbed(f, input => { seen = input; return { label: null, probability: 0, evidence: [] }; });
  assert.deepEqual(await fields.create(a, target, 'classify'), { insufficient: true, proposal: null });
  assert.deepEqual(seen, f.example.inputs);
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

async function setupReviews(t) {
  const f = setup(t);
  const publicUrl = new URL('http://dxos.invalid');
  const verify = async authorization => {
    if (authorization !== 'Bearer owner') throw new ApiError(401, 'ERR_UNAUTHORIZED', 'Unauthorized');
    return { ...a, admin: false };
  };
  const session = new CoreSession(f.store, {}, verify);
  const upstream = new CoreClient(publicUrl, session);
  f.store.setSecret(a, 'core', { accessToken: 'owner', refreshToken: 'refresh', expires: Date.now() + 60000 });
  f.core.checkSession = owner => upstream.checkSession(owner);
  const app = await createServer(f.store, verify, { publicUrl, session, fields: f.fields, records: f.records });
  f.useServer(app);
  const headers = { origin: publicUrl.origin, authorization: 'Bearer owner' };
  const review = items => app.inject({ method: 'POST', url: '/assistant-api/v1/me/reviews', headers, payload: { items } });
  return { ...f, app, headers, review, upstream };
}

test('batch review rejects an unlinked account before reading Core metadata or records', async t => {
  const f = await setupReviews(t);
  f.store.deleteSecret(a, 'core');
  const read = t.mock.method(f.core, 'read');
  const response = await f.review([target]);
  assert.equal(response.statusCode, 409, response.body);
  assert.equal(response.json().errorCode, 'ERR_CORE_LINK');
  assert.equal(response.json().data, null);
  assert.equal(read.mock.callCount(), 0);
});

test('a Core 401 fails the entire batch even if another record can be reviewed', async t => {
  const f = await setupReviews(t);
  const read = f.core.read.bind(f.core);
  f.core.read = (owner, path) => path.endsWith('/2') ? f.upstream.read(owner, path) : read(owner, path);
  for (const body of [JSON.stringify({ success: false, errorCode: 'ERR_UNAUTHENTICATED' }), 'Unauthorized']) {
    const fetch = t.mock.method(globalThis, 'fetch', async () => new Response(body, { status: 401 }));
    const response = await f.review([target, { ...target, id: 2 }]);
    assert.equal(response.statusCode, 401, response.body);
    assert.equal(response.json().errorCode, 'ERR_UNAUTHENTICATED');
    assert.equal(response.json().data, null);
    assert.equal(fetch.mock.callCount(), 1);
    fetch.mock.restore();
  }
});

test('batch review uses the caller and omits failed, read-only and closed records', async t => {
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
    if (path.endsWith('/5')) throw new ApiError(500, 'ERR_INTERNAL', 'Internal error');
    return read(owner, path);
  };
  const response = await f.review([target, ...[2, 3, 4, 5].map(id => ({ ...target, id })), { ...target, entity: 'readonly' }]);
  assert.equal(response.statusCode, 200, response.body);
  assert.equal(response.headers['cache-control'], 'no-store');
  assert.equal(response.json().data.skipped, 3);
  assert.deepEqual(response.json().data.items, [{ ...target, task: 'classify', field: 'category', current: f.record.category,
    suggested: f.example.label, confidence: f.confidence, evidence: f.example.evidence, mismatch: true }]);
  assert.equal(f.writes(), 0);
  assert.equal(f.store.proposals(a, 0, 5).length, 0);
});

test('batch review accepts at most 25 records and publishes its schemas', async t => {
  const f = await setupReviews(t);
  const items = Array.from({ length: 26 }, (_, i) => ({ ...target, id: i + 1 }));
  let metadataReads = 0, recordReads = 0, active = 0, peak = 0;
  const read = f.core.read.bind(f.core);
  f.core.read = async (owner, path) => {
    if (path.startsWith('/meta/')) metadataReads++;
    else recordReads++;
    peak = Math.max(peak, ++active);
    try {
      await new Promise(resolve => setImmediate(resolve));
      return await read(owner, path);
    } finally { active--; }
  };
  assert.equal((await f.review(items)).statusCode, 400);
  assert.equal(metadataReads + recordReads, 0);
  const accepted = await f.review(items.slice(0, 25));
  assert.equal(accepted.statusCode, 200, accepted.body);
  assert.deepEqual(accepted.json().data.items.map(({ id }) => id), items.slice(0, 25).map(({ id }) => id));
  assert.equal(accepted.json().data.skipped, 0);
  assert.equal(metadataReads, 1);
  assert.equal(recordReads, 25);
  assert.equal(peak, 5);
  const api = await f.app.inject({ method: 'GET', url: '/assistant-api/v1/openapi.json', headers: f.headers });
  const operation = api.json().paths['/assistant-api/v1/me/reviews'].post;
  assert.equal(operation.requestBody.content['application/json'].schema.properties.items.maxItems, 25);
  const dataSchema = operation.responses[200].content['application/json'].schema.properties.data;
  assert.equal(dataSchema.properties.items.items.properties.suggested.nullable, true);
  const { confidence } = dataSchema.properties.items.items.properties;
  assert.equal(confidence.type, 'number');
  assert.equal(confidence.minimum, 0);
  assert.equal(confidence.maximum, 1);
  assert.ok(dataSchema.required.includes('skipped'));
  assert.equal(dataSchema.properties.skipped.type, 'integer');
});

test('batch review omits incompatible tasks and reports matches and abstentions without flags', async t => {
  const f = await setupReviews(t);
  f.entity.fields.push({ name: 'other', type: 'choice', options: ['unrelated'] });
  f.entity.aiTasks.incompatible = { ...f.entity.aiTasks.classify, outputField: 'other' };
  f.entity.aiTasks.unloaded = { ...f.entity.aiTasks.classify, classifier: 'missing' };
  f.record.category = f.example.label;
  const match = await f.review([target]);
  assert.equal(match.statusCode, 200, match.body);
  assert.deepEqual(match.json().data.items, [{ ...target, task: 'classify', field: 'category', current: f.example.label,
    suggested: f.example.label, confidence: f.confidence, evidence: f.example.evidence, mismatch: false }]);
  Object.assign(f.record, { title: '', detail: '' });
  delete f.record.category;
  const abstention = await f.review([target]);
  assert.equal(abstention.statusCode, 200, abstention.body);
  assert.deepEqual(abstention.json().data.items, [{ ...target, task: 'classify', field: 'category', current: null,
    suggested: null, evidence: [], mismatch: false }]);
});

test('a proposal is created and confirmed through the API', async t => {
  const f = setup(t);
  const publicUrl = new URL('http://dxos.invalid');
  const app = await createServer(f.store, async () => ({ ...a, admin: false }),
    { publicUrl, session: {}, fields: f.fields, records: f.records });
  f.useServer(app);
  const me = await app.inject({ method: 'GET', url: '/assistant-api/v1/me' });
  assert.deepEqual(me.json().data.model, { configured: true,
    classifiers: ['clinic', 'itaccess', 'onestop', 'retail', 'support'] });
  const api = await app.inject({ method: 'GET', url: '/assistant-api/v1/openapi.json' });
  assert.match(api.json().info.description, /ASSISTANT_CLASSIFIERS_DIR/);
  const responses = api.json().paths['/assistant-api/v1/me/proposals'].post.responses;
  assert.match(responses[409].description, /ERR_CLASSIFIER_LABELS/);
  const snapshot = responses[200].content['application/json'].schema.properties.data.properties.proposal.properties.snapshot;
  assert.equal(snapshot.properties.confidence.type, 'number');
  const headers = { origin: publicUrl.origin };
  const created = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/proposals',
    headers, payload: { ...target, task: 'classify' } });
  assert.equal(created.statusCode, 200, created.body);
  const p = created.json().data.proposal;
  assert.equal(p.snapshot.value, f.example.label);
  assert.deepEqual(p.snapshot.evidence, f.example.evidence);
  assert.equal(p.snapshot.confidence, f.confidence);
  assert.equal(f.store.proposal(a, p.id).snapshot.confidence, f.confidence);
  assert.equal(f.writes(), 0);
  const confirmed = await app.inject({ method: 'POST', url: `/assistant-api/v1/me/proposals/${p.id}/confirm`,
    headers, payload: {} });
  assert.equal(confirmed.statusCode, 200, confirmed.body);
  assert.equal(confirmed.json().data.record.category, f.example.label);
  assert.equal(f.writes(), 1);
  Object.assign(f.record, { title: '', detail: '' });
  const abstention = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/proposals',
    headers, payload: { ...target, task: 'classify' } });
  assert.equal(abstention.statusCode, 200, abstention.body);
  assert.deepEqual(abstention.json().data, { insufficient: true, proposal: null });
});

test('missing or invalid weights, mismatched labels and abstentions affect only their task', async t => {
  const f = setup(t);
  f.entity.aiTasks.missing = { ...f.entity.aiTasks.classify, classifier: 'missing' };
  await assert.rejects(f.fields.create(a, target, 'missing'), { status: 409, errorCode: 'ERR_CLASSIFIER_UNAVAILABLE' });
  const field = f.entity.fields.find(x => x.name === 'category');
  const options = field.options;
  for (const invalid of [options.slice(1), [...options, 'other'], [...options.slice(1), 'wrong']]) {
    field.options = invalid;
    await assert.rejects(f.fields.create(a, target, 'classify'), { status: 409, errorCode: 'ERR_CLASSIFIER_LABELS' });
  }
  field.options = options;
  const path = join(f.dir, 'support.json');
  writeFileSync(path, readFileSync(supportWeights));
  writeFileSync(join(f.dir, 'broken.json'), '{}');
  const fromDisk = loadClassifiers(f.dir);
  assert.deepEqual([...fromDisk.keys()], ['support']);
  const fields = new FieldProposals(f.records, f.core, f.store, fromDisk);
  await assert.rejects(fields.create(a, target, 'missing'), { status: 409, errorCode: 'ERR_CLASSIFIER_UNAVAILABLE' });
  assert.equal((await fields.create(a, target, 'classify')).proposal.snapshot.value, f.example.label);
  Object.assign(f.record, { title: '', detail: null });
  assert.deepEqual(await fields.create(a, target, 'classify'), { insufficient: true, proposal: null });
  assert.equal(f.writes(), 0);
});

test('evidence from another field is rejected', async t => {
  const f = setup(t);
  f.record.private_note = 'undeclared source';
  const fields = stubbed(f, () => ({ label: f.example.label, probability: 1, evidence: [f.record.private_note] }));
  await assert.rejects(fields.create(a, target, 'classify'), { errorCode: 'ERR_CLASSIFIER_OUTPUT' });
  assert.equal(f.store.proposals(a, 0, 5).length, 0);
});
