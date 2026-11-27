// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, existsSync, chmodSync, lstatSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import { own, write } from './files.js';

const [operation, root] = process.argv.slice(2);
if (operation !== 'prepare' || !root || !isAbsolute(root) || root === '/') throw new Error('Use prepare with a private absolute directory');
const privateDir = join(root, 'private');
function directory(path: string) {
  if (existsSync(path) && (lstatSync(path).isSymbolicLink() || !lstatSync(path).isDirectory()))
    throw new Error('Use ordinary private state directories');
  mkdirSync(path, { recursive: true, mode: 0o700 });
  chmodSync(path, 0o700);
}
for (const path of [root, privateDir, join(root, 'control'), join(root, 'sso')]) directory(path);
const publicUrl = new URL(process.env.ASSISTANT_PUBLIC_URL ?? '');
if (!['http:', 'https:'].includes(publicUrl.protocol) || publicUrl.username || publicUrl.password ||
    publicUrl.pathname !== '/' || publicUrl.search || publicUrl.hash) throw new Error('Use the public gateway origin');
const master = join(privateDir, 'master.key');
if (existsSync(join(root, 'control/assistant.sqlite')) && !existsSync(master))
  throw new Error('Restore the existing master key before starting');
for (const path of [master]) {
  if (existsSync(path) && (!lstatSync(path).isFile() || lstatSync(path).isSymbolicLink()))
    throw new Error('Restore ordinary private key and capability files');
}
if (!existsSync(master)) write(master, randomBytes(32));
if (readFileSync(master).length !== 32) throw new Error('The existing master key is invalid');
const client = { clientId: 'dxos-assistant', name: 'DX-OS business assistant', protocol: 'openid-connect',
  publicClient: true, fullScopeAllowed: true, standardFlowEnabled: true, implicitFlowEnabled: false,
  directAccessGrantsEnabled: false, serviceAccountsEnabled: false,
  redirectUris: [new URL('/assistant-api/v1/oauth/callback', publicUrl).toString()],
  webOrigins: [publicUrl.origin], attributes: { 'pkce.code.challenge.method': 'S256', 'dxos.assistant.managed': 'true' },
  defaultClientScopes: ['basic', 'web-origins', 'acr', 'profile', 'roles', 'email'],
  protocolMappers: [
    { name: 'dxos-api-audience', protocol: 'openid-connect', protocolMapper: 'oidc-audience-mapper',
      config: { 'included.custom.audience': 'dxos-api', 'id.token.claim': 'false', 'access.token.claim': 'true' } },
    { name: 'groups', protocol: 'openid-connect', protocolMapper: 'oidc-group-membership-mapper',
      config: { 'full.path': 'false', 'id.token.claim': 'false', 'access.token.claim': 'true',
        'claim.name': 'groups', 'userinfo.token.claim': 'false' } },
  ] };
write(join(root, 'sso/client.json'), JSON.stringify(client));
if (process.getuid?.() === 0) own(root);
console.log('Assistant state is ready; existing keys and account data were kept.');
