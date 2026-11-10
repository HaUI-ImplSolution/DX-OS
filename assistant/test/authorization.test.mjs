// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer as httpServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { generateKeyPair, exportJWK, SignJWT } from 'jose';
import { identityVerifier } from '../dist/auth/identity.js';
import { Store } from '../dist/storage/store.js';
import { createServer } from '../dist/server.js';
import { Profiles } from '../dist/providers/profiles.js';
import { Runtimes } from '../dist/runtime/slots.js';
import { TelegramAccounts } from '../dist/accounts/telegram.js';

test('real JWT verification and owner-scoped API reject wrong audience and cross-account profiles', async t => {
  const { privateKey, publicKey } = await generateKeyPair('RS256');
  const jwk = { ...await exportJWK(publicKey), kid: 'synthetic-test-key', alg: 'RS256' };
  const server = httpServer((_request, response) => { response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ keys: [jwk] })); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const issuer = 'https://identity.invalid/realms/demo';
  const verify = identityVerifier(issuer, 'assistant', new URL(`http://127.0.0.1:${server.address().port}`));
  const token = (subject, audience = 'assistant') => new SignJWT({}).setProtectedHeader({ alg: 'RS256', kid: jwk.kid })
    .setSubject(subject).setIssuer(issuer).setAudience(audience).setIssuedAt().setExpirationTime('2m').sign(privateKey);
  await assert.rejects(verify(`Bearer ${await token('A', 'other-client')}`), { status: 401 });
  const rejectedToken = (iss, exp) => new SignJWT({}).setProtectedHeader({ alg: 'RS256', kid: jwk.kid })
    .setSubject('A').setIssuer(iss).setAudience('assistant').setIssuedAt().setExpirationTime(exp).sign(privateKey);
  await assert.rejects(verify(`Bearer ${await rejectedToken('https://other.invalid', '2m')}`), { status: 401 });
  await assert.rejects(verify(`Bearer ${await rejectedToken(issuer, Math.floor(Date.now() / 1000) - 60)}`), { status: 401 });

  const dir = mkdtempSync(join(tmpdir(), 'dxos-auth-test-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const runtimes = new Runtimes([], () => { throw new Error('No runtime in this test'); }, store);
  const profiles = new Profiles(store, runtimes);
  const a = { issuer, subject: 'A' };
  const id = await profiles.save(a, { provider: 'gemini', model: 'synthetic', apiKey: 'synthetic-secret' });
  const publicUrl = new URL('http://dxos.invalid');
  const app = await createServer(store, verify, { publicUrl, session: {}, accounts: {
    store, runtimes, profiles, telegram: new TelegramAccounts(store, runtimes), records: {}, publicUrl, verify,
  } });
  t.after(async () => { await app.close(); rmSync(dir, { recursive: true, force: true }); });
  const aToken = await token('A'), bToken = await token('B');
  const own = await app.inject({ method: 'GET', url: '/assistant-api/v1/me/providers', headers: { authorization: `Bearer ${aToken}` } });
  assert.equal(own.statusCode, 200); assert.equal(own.body.includes('synthetic-secret'), false);
  assert.equal(own.json().data.profiles[0].id, id);
  const other = await app.inject({ method: 'GET', url: '/assistant-api/v1/me/providers', headers: { authorization: `Bearer ${bToken}` } });
  assert.equal(other.json().data.profiles.length, 0);
  const cross = await app.inject({ method: 'DELETE', url: `/assistant-api/v1/me/providers/${id}`,
    headers: { authorization: `Bearer ${bToken}`, origin: publicUrl.origin } });
  assert.equal(cross.statusCode, 404);
  const csrf = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/runtime/start',
    headers: { authorization: `Bearer ${aToken}`, origin: 'http://other.invalid' }, payload: {} });
  assert.equal(csrf.statusCode, 403);
});

test('unlink keeps bot ownership when its runtime cannot be stopped', async t => {
  let unavailable = true;
  const supervisor = httpServer((_request, response) => {
    response.statusCode = unavailable ? 503 : 200;
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify(unavailable ? { errorCode: 'ERR_RUNTIME_BUSY' } : { active: false }));
  });
  await new Promise(resolve => supervisor.listen(0, '127.0.0.1', resolve));
  const dir = mkdtempSync(join(tmpdir(), 'dxos-unlink-test-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  t.after(async () => { store.close(); supervisor.closeAllConnections(); await new Promise(resolve => supervisor.close(resolve)); rmSync(dir, { recursive: true, force: true }); });
  const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
  const runtime = new Runtimes([{ name: 'one', url: `http://127.0.0.1:${supervisor.address().port}`,
    controlToken: randomBytes(32).toString('hex'), toolToken: randomBytes(32).toString('hex'), confirmationToken: randomBytes(32).toString('hex') }], () => ({}), store);
  store.allocateRuntime(a, ['one']); store.bindTelegram(a, '100', { token: 'synthetic' });
  const accounts = new TelegramAccounts(store, runtime);
  await assert.rejects(accounts.unlink(a), { status: 503 });
  assert.throws(() => store.bindTelegram(b, '100', {}), { errorCode: 'ERR_BOT_IN_USE' });
  assert.equal(store.getSecret(a, 'telegram').token, 'synthetic');
  unavailable = false; await accounts.unlink(a);
  store.bindTelegram(b, '100', { token: 'synthetic' });
  assert.equal(store.getSecret(a, 'telegram'), undefined);
});

test('the bridge preserves explicit null when returning a record to its handler group', async t => {
  const { default: Fastify } = await import('fastify');
  const { bridgeRoutes } = await import('../dist/tools/bridge.js');
  const dir = mkdtempSync(join(tmpdir(), 'dxos-null-assignee-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const owner = { issuer: 'https://identity.invalid', subject: 'A' };
  store.setSecret(owner, 'telegram', { userId: '100' });
  const app = Fastify();
  bridgeRoutes(app, { authorize: () => owner }, {
    propose: async (_owner, _target, input) => ({ assignee: input.assignee }),
  }, {}, store);
  t.after(async () => { await app.close(); store.close(); rmSync(dir, { recursive: true, force: true }); });
  for (const assignee of [null, 'synthetic-handler']) {
    const response = await app.inject({ method: 'POST', url: '/internal/v1/tools',
      payload: { tool: 'propose', model: 'demo', entity: 'request', id: 1, operation: 'assign', assignee } });
    assert.equal(response.statusCode, 200);
    assert.equal(response.json().data.assignee, assignee);
  }
});

test('runtime capabilities bind bridge reads and confirmations to their owner', async t => {
  const { default: Fastify } = await import('fastify');
  const { bridgeRoutes } = await import('../dist/tools/bridge.js');
  const dir = mkdtempSync(join(tmpdir(), 'dxos-runtime-boundary-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
  const slots = ['one', 'two'].map(name => ({ name, url: `http://${name}.invalid`,
    controlToken: randomBytes(32).toString('hex'), toolToken: randomBytes(32).toString('hex'),
    confirmationToken: randomBytes(32).toString('hex') }));
  const runtimes = new Runtimes(slots, () => ({}), store);
  store.allocateRuntime(a, ['one', 'two']); store.allocateRuntime(b, ['one', 'two']);
  store.setSecret(a, 'telegram', { userId: '100' }); store.setSecret(b, 'telegram', { userId: '200' });
  const proposal = store.createProposal(b, { operation: 'action', summary: 'synthetic' });
  const app = Fastify();
  app.setErrorHandler((error, _request, reply) => reply.code(error.status ?? error.statusCode ?? 500).send({ errorCode: error.errorCode }));
  bridgeRoutes(app, runtimes, { models: async owner => ({ subject: owner.subject }),
    execute: async (owner, id) => store.claim(owner, id, 'telegram') }, {}, store);
  t.after(async () => { await app.close(); store.close(); rmSync(dir, { recursive: true, force: true }); });
  const call = (path, token, payload) => app.inject({ method: 'POST', url: `/internal/v1/${path}`,
    headers: { authorization: `Bearer ${token}` }, payload });
  for (const [index, subject] of ['A', 'B'].entries()) {
    const read = await call('tools', slots[index].toolToken, { tool: 'models' });
    assert.equal(read.json().data.subject, subject);
  }
  const callback = { proposalId: proposal, userId: '100', chatId: '100' };
  assert.equal((await call('confirm', slots[0].toolToken, callback)).statusCode, 401);
  assert.equal((await call('confirm', slots[0].confirmationToken, callback)).statusCode, 404);
  assert.equal((await call('confirm', slots[0].confirmationToken, { ...callback, userId: '200', chatId: '200' })).statusCode, 403);
  const forgedOwner = await call('tools', slots[0].toolToken, { tool: 'models', ownerId: 'B' });
  assert.equal(forgedOwner.json().data.subject, 'A');
  assert.equal((await call('tools', slots[0].controlToken, { tool: 'models' })).statusCode, 401);
  assert.equal((await call('tools', slots[0].toolToken, { tool: 'shell' })).statusCode, 400);
  assert.equal(store.proposal(b, proposal).state, 'pending');
});

test('custom endpoints cannot reach probe or runtime, including persisted profiles', async t => {
  const { modelConfiguration } = await import('../dist/providers/profiles.js');
  const { runtimeConfiguration } = await import('../dist/runtime/configuration.js');
  const dir = mkdtempSync(join(tmpdir(), 'dxos-provider-boundary-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  t.after(() => { store.close(); rmSync(dir, { recursive: true, force: true }); });
  const owner = { issuer: 'https://identity.invalid', subject: 'A' };
  const profiles = new Profiles(store, { update: () => { throw new Error('Must reject before runtime access'); } });
  for (const [provider, id, api, baseUrl] of [
    ['gemini', 'google', 'google-generative-ai', 'https://generativelanguage.googleapis.com/v1beta'],
    ['openai', 'openai', 'openai-responses', 'https://api.openai.com/v1'],
    ['anthropic', 'anthropic', 'anthropic-messages', 'https://api.anthropic.com'],
  ]) {
    const profile = { provider, model: `${id}/synthetic`, apiKey: 'synthetic-sensitive-key' };
    const configured = modelConfiguration(profile);
    assert.equal(configured.primary, `${id}/synthetic`);
    assert.equal(configured.providers[id].baseUrl, baseUrl);
    assert.equal(configured.providers[id].api, api);
    assert.equal(JSON.stringify(configured).includes(profile.apiKey), false);
    const runtime = runtimeConfiguration(profile, '100', 18791, '/state', '/plugin');
    assert.deepEqual(runtime.agents.defaults.model.fallbacks, []);
    assert.equal(runtime.tools.allow.includes('dxos_propose'), true);
  }
  for (const baseUrl of ['https://127.0.0.1/v1', 'https://[::1]/v1', 'https://169.254.169.254/v1',
    'https://10.0.0.1/v1', 'https://redirect.invalid/v1', 'https://dns-rebinding.invalid/v1']) {
    const profile = { id: 'legacy', provider: 'compatible', baseUrl, apiKey: 'synthetic', model: 'test' };
    await assert.rejects(profiles.save(owner, profile), { errorCode: 'ERR_PROVIDER_DISABLED' });
    store.setSecret(owner, 'providers', { profiles: [profile], selected: profile.id });
    assert.throws(() => profiles.active(owner), { errorCode: 'ERR_PROVIDER_DISABLED' });
    assert.throws(() => profiles.get(owner, profile.id), { errorCode: 'ERR_PROVIDER_DISABLED' });
    assert.throws(() => modelConfiguration(profile), { errorCode: 'ERR_PROVIDER_DISABLED' });
    assert.throws(() => runtimeConfiguration(profile, '100', 18791, '/state', '/plugin'), { errorCode: 'ERR_PROVIDER_DISABLED' });
  }
});

test('framework client errors keep HTTP status and server errors remain redacted', async t => {
  const { CoreSession } = await import('../dist/auth/core-session.js');
  const dir = mkdtempSync(join(tmpdir(), 'dxos-http-errors-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const owner = { issuer: 'https://identity.invalid', subject: 'A' };
  const verify = async value => { if (value === 'Bearer server-failure') throw new Error('synthetic-sensitive-error'); return owner; };
  const runtimes = new Runtimes([], () => ({}), store);
  const publicUrl = new URL('http://dxos.invalid');
  const app = await createServer(store, verify, { session: {}, publicUrl, accounts: {
    store, runtimes, profiles: new Profiles(store, runtimes), telegram: new TelegramAccounts(store, runtimes), publicUrl, verify,
  } });
  t.after(async () => { await app.close(); rmSync(dir, { recursive: true, force: true }); });
  for (const [payload, expected] of [['{not json', 400], [JSON.stringify({ apiKey: 'x'.repeat(40000) }), 413]]) {
    const response = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/providers',
      headers: { origin: publicUrl.origin, authorization: 'Bearer synthetic', 'content-type': 'application/json' }, payload });
    assert.equal(response.statusCode, expected);
    assert.equal(response.json().success, false);
    assert.equal(response.body.includes(payload), false);
  }
  const headers = { origin: publicUrl.origin, authorization: 'Bearer synthetic' };
  const created = await app.inject({ method: 'POST', url: '/assistant-api/v1/me/providers', headers,
    payload: { provider: 'gemini', model: 'old', apiKey: 'synthetic-private-key', ownerId: 'B' } });
  const id = created.json().data.id;
  const partial = await app.inject({ method: 'PATCH', url: `/assistant-api/v1/me/providers/${id}`, headers, payload: { model: 'new' } });
  assert.equal(partial.statusCode, 400);
  const full = await app.inject({ method: 'PATCH', url: `/assistant-api/v1/me/providers/${id}`, headers,
    payload: { provider: 'gemini', model: 'new', apiKey: 'synthetic-private-key' } });
  assert.equal(full.statusCode, 200); assert.equal(full.body.includes('synthetic-private-key'), false);
  assert.equal(store.getSecret(owner, 'providers').profiles[0].model, 'new');
  const failure = await app.inject({ method: 'GET', url: '/assistant-api/v1/me', headers: { authorization: 'Bearer server-failure' } });
  assert.equal(failure.statusCode, 500); assert.equal(failure.json().errorCode, 'ERR_INTERNAL');
  assert.equal(failure.body.includes('synthetic-sensitive-error'), false);
  const session = new CoreSession(store, {}, verify);
  await assert.rejects(session.accessToken(owner), { status: 409, errorCode: 'ERR_CORE_LINK' });
});

function coreSessionFixture(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-core-session-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const requests = [];
  let status = 200, subject = 'A';
  const server = httpServer(async (request, response) => {
    let body = ''; for await (const part of request) body += part;
    requests.push(new URLSearchParams(body));
    response.statusCode = status;
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify(status === 200
      ? { access_token: subject, refresh_token: 'synthetic-refresh', expires_in: 120 }
      : { error: 'synthetic-sensitive-provider-error' }));
  });
  t.after(async () => { store.close(); server.closeAllConnections();
    await new Promise(resolve => server.close(resolve)); rmSync(dir, { recursive: true, force: true }); });
  return { store, requests, server, status: value => { status = value; }, subject: value => { subject = value; } };
}

test('Core linking uses PKCE, rejects a different identity and consumes the browser flow once', async t => {
  const { createHash } = await import('node:crypto');
  const { CoreSession } = await import('../dist/auth/core-session.js');
  const f = coreSessionFixture(t);
  await new Promise(resolve => f.server.listen(0, '127.0.0.1', resolve));
  const owner = { issuer: 'https://identity.invalid', subject: 'A' };
  const session = new CoreSession(f.store, { authorizationUrl: owner.issuer + '/auth',
    tokenUrl: `http://127.0.0.1:${f.server.address().port}/token`, clientId: 'assistant-core', callbackUrl: 'https://dxos.invalid/callback' },
    async token => ({ issuer: owner.issuer, subject: token.slice(7) }));
  let flow = session.start(owner), url = new URL(flow.url);
  f.subject('B');
  await assert.rejects(session.callback(url.searchParams.get('state'), flow.cookie, 'synthetic-code'), { errorCode: 'ERR_LINK_IDENTITY' });
  assert.equal(f.store.getSecret(owner, 'core'), undefined);
  await assert.rejects(session.callback(url.searchParams.get('state'), flow.cookie, 'synthetic-code'), { errorCode: 'ERR_LINK_EXPIRED' });
  f.subject('A'); flow = session.start(owner); url = new URL(flow.url);
  await session.callback(url.searchParams.get('state'), flow.cookie, 'synthetic-code');
  const exchange = f.requests.at(-1);
  assert.equal(exchange.get('grant_type'), 'authorization_code');
  assert.equal(exchange.get('client_id'), 'assistant-core');
  assert.equal(createHash('sha256').update(exchange.get('code_verifier')).digest('base64url'), url.searchParams.get('code_challenge'));
  assert.equal(await session.accessToken(owner), 'A');
});

test('concurrent refresh uses one exchange and revocation clears only the affected account', async t => {
  const { CoreSession } = await import('../dist/auth/core-session.js');
  const f = coreSessionFixture(t);
  await new Promise(resolve => f.server.listen(0, '127.0.0.1', resolve));
  const owner = { issuer: 'https://identity.invalid', subject: 'A' }, other = { ...owner, subject: 'B' };
  const session = new CoreSession(f.store, { authorizationUrl: owner.issuer + '/auth',
    tokenUrl: `http://127.0.0.1:${f.server.address().port}/token`, clientId: 'assistant-core' },
    async token => ({ issuer: owner.issuer, subject: token.slice(7) }));
  const expired = { accessToken: 'A', refreshToken: 'synthetic-refresh', expires: 0 };
  f.store.setSecret(owner, 'core', expired);
  f.store.setSecret(other, 'core', { ...expired, accessToken: 'B', expires: Date.now() + 120000 });
  assert.deepEqual(await Promise.all(Array.from({ length: 8 }, () => session.accessToken(owner))), Array(8).fill('A'));
  assert.equal(f.requests.length, 1);
  f.store.setSecret(owner, 'core', expired); f.status(401);
  await assert.rejects(session.accessToken(owner), error => error.status === 401 && !error.message.includes('synthetic-sensitive'));
  assert.equal(f.store.getSecret(owner, 'core'), undefined);
  assert.equal(await session.accessToken(other), 'B');
  f.store.setSecret(owner, 'core', expired); f.status(503);
  await assert.rejects(session.accessToken(owner), { status: 503 });
  assert.deepEqual(f.store.getSecret(owner, 'core'), expired);
});

test('changing account B does not block or stop account A with a request in flight', async t => {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-runtime-lifecycle-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  let release, received;
  const arrived = new Promise(resolve => { received = resolve; });
  const waiting = new Promise(resolve => { release = resolve; });
  let stoppedA = 0, stoppedB = 0;
  const aServer = httpServer(async (request, response) => {
    if (request.url === '/start') { received(); await waiting; }
    if (request.url === '/stop') stoppedA++;
    response.setHeader('Content-Type', 'application/json'); response.end('{"active":true}');
  });
  const bServer = httpServer((request, response) => {
    if (request.url === '/stop') stoppedB++;
    response.setHeader('Content-Type', 'application/json'); response.end('{"active":false}');
  });
  for (const server of [aServer, bServer]) await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { release(); for (const server of [aServer, bServer]) {
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
  } store.close(); rmSync(dir, { recursive: true, force: true }); });
  const slots = [aServer, bServer].map((server, index) => ({ name: `slot-${index}`,
    url: `http://127.0.0.1:${server.address().port}`, controlToken: randomBytes(32).toString('hex'), toolToken: randomBytes(32).toString('hex'),
    confirmationToken: randomBytes(32).toString('hex') }));
  const runtime = new Runtimes(slots, () => ({}), store), profiles = new Profiles(store, runtime);
  const a = { issuer: 'https://identity.invalid', subject: 'A' }, b = { ...a, subject: 'B' };
  const started = runtime.start(a); await arrived;
  await runtime.start(b);
  await profiles.save(b, { provider: 'gemini', model: 'synthetic', apiKey: 'synthetic-private' });
  assert.equal(stoppedA, 0); assert.equal(stoppedB, 1);
  assert.equal(store.getSecret(a, 'runtimeEnabled'), true);
  assert.equal(store.runtimeSlot(a), 'slot-0'); assert.equal(store.runtimeSlot(b), 'slot-1');
  release(); await started;
});

test('Telegram pairing is scoped to the configured bot account', async () => {
  const { runtimeConfiguration } = await import('../dist/runtime/configuration.js');
  const profile = { provider: 'gemini', model: 'synthetic', apiKey: 'synthetic' };
  const first = runtimeConfiguration(profile, '100', 18791, '/state', '/plugin', 'bot-11111');
  const second = runtimeConfiguration(profile, undefined, 18791, '/state', '/plugin', 'bot-22222');
  assert.notEqual(first.channels.telegram.defaultAccount, second.channels.telegram.defaultAccount);
  assert.deepEqual(Object.keys(second.channels.telegram.accounts), ['bot-22222']);
  assert.equal(second.channels.telegram.dmPolicy, 'pairing');
  assert.equal(second.channels.telegram.allowFrom, undefined);
  assert.equal(second.plugins.entries['dxos-assistant'].enabled, false);
});

test('reconfiguring the same Telegram bot invalidates its previous pairing scope', async t => {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-bot-relink-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const previous = globalThis.fetch;
  t.after(() => { globalThis.fetch = previous; store.close(); rmSync(dir, { recursive: true, force: true }); });
  globalThis.fetch = async url => new Response(JSON.stringify({ ok: true,
    result: String(url).endsWith('/getMe') ? { id: 12345, username: 'synthetic_bot' } : { url: '' } }));
  const accounts = new TelegramAccounts(store, { update: async (_owner, change) => change() });
  const owner = { issuer: 'https://identity.invalid', subject: 'A' };
  const token = '12345:' + 's'.repeat(30);
  await accounts.configure(owner, token);
  const first = store.getSecret(owner, 'telegram');
  store.setSecret(owner, 'telegram', { ...first, userId: '100' });
  await accounts.configure(owner, token);
  const second = store.getSecret(owner, 'telegram');
  assert.notEqual(second.accountId ?? `bot-${second.botId}`, first.accountId ?? `bot-${first.botId}`);
  assert.equal(accounts.status(owner).paired, false);
  const { runtimeConfiguration } = await import('../dist/runtime/configuration.js');
  const runtime = runtimeConfiguration({ provider: 'gemini', model: 'synthetic', apiKey: 'synthetic' }, undefined,
    18791, '/state', '/plugin', second.accountId);
  assert.equal(runtime.channels.telegram.defaultAccount, second.accountId);
});
