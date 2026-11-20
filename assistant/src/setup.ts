// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync, chmodSync, chownSync, lstatSync, readdirSync, renameSync, rmSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';

const [operation, root] = process.argv.slice(2);
if (operation !== 'prepare' || !root || !isAbsolute(root) || root === '/') throw new Error('Use prepare with a private absolute directory');
const privateDir = join(root, 'private');
function directory(path: string) {
  if (existsSync(path) && (lstatSync(path).isSymbolicLink() || !lstatSync(path).isDirectory()))
    throw new Error('Use ordinary private state directories');
  mkdirSync(path, { recursive: true, mode: 0o700 });
  chmodSync(path, 0o700);
}
function write(path: string, value: string | Buffer) {
  if (existsSync(path) && (!lstatSync(path).isFile() || lstatSync(path).isSymbolicLink()))
    throw new Error('Use ordinary private configuration files');
  const data = Buffer.isBuffer(value) ? value : Buffer.from(value);
  if (!existsSync(path) || !readFileSync(path).equals(data)) {
    const temporary = path + '.' + randomBytes(8).toString('hex') + '.tmp';
    try {
      writeFileSync(temporary, data, { mode: 0o600, flag: 'wx' });
      renameSync(temporary, path);
    } finally { rmSync(temporary, { force: true }); }
  }
  chmodSync(path, 0o600);
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
// The service uses the node uid/gid 1000.
function own(path: string) {
  const entry = lstatSync(path);
  if (entry.isSymbolicLink()) return;
  if (entry.uid !== 1000 || entry.gid !== 1000) chownSync(path, 1000, 1000);
  if (entry.isDirectory()) for (const name of readdirSync(path)) own(join(path, name));
}
if (process.getuid?.() === 0) own(root);
console.log('Assistant state is ready; existing keys and account data were kept.');
