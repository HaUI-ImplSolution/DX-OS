// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync, chmodSync, chownSync, lstatSync, readdirSync, renameSync, rmSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import type { Slot } from './runtime/slots.js';

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
const master = join(privateDir, 'master.key'), slotFile = join(privateDir, 'slots.json');
if (existsSync(join(root, 'control/assistant.sqlite')) && (!existsSync(master) || !existsSync(slotFile)))
  throw new Error('Restore the existing master key and runtime capabilities before starting');
for (const path of [master, slotFile]) {
  if (existsSync(path) && (!lstatSync(path).isFile() || lstatSync(path).isSymbolicLink()))
    throw new Error('Restore ordinary private key and capability files');
}
if (!existsSync(master)) write(master, randomBytes(32));
if (readFileSync(master).length !== 32) throw new Error('The existing master key is invalid');
const slots: Slot[] = existsSync(slotFile) ? JSON.parse(readFileSync(slotFile, 'utf8')) : [];
if (!Array.isArray(slots) || slots.length > 3 || slots.some(slot => !/^runtime-[123]$/.test(slot.name)))
  throw new Error('The existing runtime configuration is invalid');
const tokens = slots.flatMap(slot => [slot.controlToken, slot.toolToken, slot.confirmationToken]);
if (new Set(slots.map(slot => slot.name)).size !== slots.length || new Set(tokens).size !== tokens.length ||
    tokens.some(token => typeof token !== 'string' || token.length < 32)) throw new Error('Runtime capabilities must remain distinct');
for (let i = 1; i <= 3; i++) {
  const name = `runtime-${i}`, state = join(root, name); directory(state);
  let slot = slots.find(slot => slot.name === name);
  if (!slot) {
    slot = { name, url: `http://assistant-${name}:18792`, controlToken: randomBytes(32).toString('base64url'),
      toolToken: randomBytes(32).toString('base64url'), confirmationToken: randomBytes(32).toString('base64url') };
    slots.push(slot);
  }
  if (slot.url !== `http://assistant-${name}:18792`) throw new Error('Restore the original runtime endpoint configuration');
  const values = { ASSISTANT_RUNTIME_HOST: '0.0.0.0', ASSISTANT_RUNTIME_PORT: '18792', ASSISTANT_GATEWAY_PORT: '18791',
    ASSISTANT_RUNTIME_STATE_DIR: '/state', ASSISTANT_OPENCLAW_MODULE: '/opt/openclaw/node_modules/openclaw/openclaw.mjs',
    ASSISTANT_OPENCLAW_PLUGIN: '/app/runtime/plugin', ASSISTANT_PROMPT_FILE: '/app/runtime/AGENTS.md',
    ASSISTANT_BRIDGE_URL: 'http://assistant-control:18790', ASSISTANT_RUNTIME_CONTROL_TOKEN: slot.controlToken,
    ASSISTANT_RUNTIME_TOOL_TOKEN: slot.toolToken, ASSISTANT_RUNTIME_CONFIRMATION_TOKEN: slot.confirmationToken };
  write(join(state, 'runtime.env'), Object.entries(values).map(([key, value]) => `${key}=${value}`).join('\n') + '\n');
}
slots.sort((left, right) => left.name.localeCompare(right.name));
write(slotFile, JSON.stringify(slots)); chmodSync(master, 0o600);
const client = { clientId: 'dxos-assistant', name: 'DX-OS personal assistant', protocol: 'openid-connect',
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
// The image runs control and supervisors as node (uid/gid 1000).
function own(path: string) {
  const entry = lstatSync(path);
  if (entry.isSymbolicLink()) return;
  if (entry.uid !== 1000 || entry.gid !== 1000) chownSync(path, 1000, 1000);
  if (entry.isDirectory()) for (const name of readdirSync(path)) own(join(path, name));
}
if (process.getuid?.() === 0) own(root);
console.log('Assistant state is ready; existing keys and account data were kept.');
