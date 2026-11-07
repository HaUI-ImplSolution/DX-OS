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
  const profiles = new Profiles(store, [], runtimes);
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
