// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
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
import { Records } from '../dist/tools/records.js';
import { FieldProposals } from '../dist/tools/fields.js';

async function listen(t, handler) {
  const server = httpServer(handler); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  return `http://127.0.0.1:${server.address().port}`;
}
test('the API rejects bad tokens and origins, and one user cannot read or confirm another user\'s proposal', async t => {
  const { privateKey, publicKey } = await generateKeyPair('RS256');
  const jwk = { ...await exportJWK(publicKey), kid: 'synthetic-key', alg: 'RS256' };
  const endpoint = await listen(t, (_req, res) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ keys: [jwk] })); });
  const issuer = 'https://identity.invalid/realms/demo';
  const verify = identityVerifier(issuer, 'assistant', new URL(endpoint));
  const token = (subject, aud = 'assistant', iss = issuer, expiry = '2m') => new SignJWT({}).setProtectedHeader({ alg: 'RS256', kid: jwk.kid })
    .setSubject(subject).setIssuer(iss).setAudience(aud).setIssuedAt().setExpirationTime(expiry).sign(privateKey);
  for (const invalid of [await token('A','wrong'),await token('A','assistant','https://other.invalid'),await token('A','assistant',issuer,0)])
    await assert.rejects(verify(`Bearer ${invalid}`), { status: 401 });
  const dir = mkdtempSync(join(tmpdir(), 'dxos-auth-')), store = new Store(join(dir,'private/state.sqlite'),randomBytes(32));
  const core = { async read(_owner,path) { return path.startsWith('/meta/') ? {
    name: 'record', label: 'Record', canRead: true, canWrite: true, modelVersion: 1,
    fields: [{ name: 'detail', type: 'text' },{ name: 'category', type: 'choice', options: ['a'] }],
    aiTasks: { classify: { label: 'Classify', classifier: 'demo', inputFields: ['detail'], outputField: 'category' } },
  } : { version: 0, detail: 'Synthetic', category: 'a' }; }, async patch() { assert.fail('No write expected'); } };
  const classifier = { labels: ['a'], revision: '1', predict: () => ({ label: 'a', probability: 1, evidence: ['Synthetic'] }) };
  const records = new Records(core,store), fields = new FieldProposals(records,core,store,new Map([['demo',classifier]]));
  const app = await createServer(store,verify,{ publicUrl: new URL('http://dxos.invalid'), session: {}, core, records, fields });
  t.after(async () => { await app.close(); rmSync(dir,{ recursive:true,force:true }); });
  const a = await token('A'), b = await token('B');
  const headers = { authorization: `Bearer ${a}`, origin: 'http://dxos.invalid' };
  const payload = { model:'demo',entity:'record',id:1,task:'classify' };
  const unauthorized = await app.inject({ method:'POST',url:'/assistant-api/v1/me/proposals',headers:{ origin:headers.origin },payload });
  assert.equal(unauthorized.statusCode,401);
  const origin = await app.inject({ method:'POST',url:'/assistant-api/v1/me/proposals',headers:{ ...headers,origin:'http://other.invalid' },payload });
  assert.equal(origin.statusCode,403);
  const created = await app.inject({ method:'POST',url:'/assistant-api/v1/me/proposals',headers,payload });
  assert.equal(created.statusCode,200,created.body); const id = created.json().data.proposal.id;
  const other = await app.inject({ method:'GET',url:`/assistant-api/v1/me/proposals/${id}`,headers:{ authorization:`Bearer ${b}` } });
  assert.equal(other.statusCode,404);
  const stolen = await app.inject({ method:'POST',url:`/assistant-api/v1/me/proposals/${id}/confirm`,headers:{ ...headers,authorization:`Bearer ${b}` },payload:{} });
  assert.equal(stolen.statusCode,404);
});
