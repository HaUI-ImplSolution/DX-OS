// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync, chmodSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import type { Slot } from './runtime/slots.js';

const [operation, root, countText] = process.argv.slice(2);
if (!root || !isAbsolute(root) || root === '/') throw new Error('Use a private absolute state directory');
const privateDir = join(root, 'private');
if (operation === 'prepare') {
  const count = Number(countText);
  if (![1, 2, 3].includes(count)) throw new Error('Choose one, two or three runtime slots');
  mkdirSync(root, { recursive: true, mode: 0o700 }); chmodSync(root, 0o700);
  for (const path of [privateDir, join(root, 'control')]) { mkdirSync(path, { recursive: true, mode: 0o700 }); chmodSync(path, 0o700); }
  const publicUrl = new URL(process.env.DXOS_ASSISTANT_PUBLIC_URL ?? '');
  if (!['http:', 'https:'].includes(publicUrl.protocol) || publicUrl.username || publicUrl.password ||
      publicUrl.pathname !== '/' || publicUrl.search || publicUrl.hash) throw new Error('Use the public gateway origin');
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
  writeFileSync(join(privateDir, 'oidc-client.json'), JSON.stringify(client), { mode: 0o600 });
  const master = join(privateDir, 'master.key');
  if (!existsSync(master)) {
    if (existsSync(join(root, 'control/assistant.sqlite'))) throw new Error('Restore the existing master key; do not replace it');
    writeFileSync(master, randomBytes(32), { flag: 'wx', mode: 0o600 });
  }
  const slotFile = join(privateDir, 'slots.json');
  const slots: Slot[] = existsSync(slotFile) ? JSON.parse(readFileSync(slotFile, 'utf8')) : [];
  if (slots.length > count) throw new Error('Reducing the slot count requires an explicit data removal procedure');
  const services: Record<string, unknown> = {}, networks: Record<string, unknown> = {};
  for (let i = 1; i <= count; i++) {
    const name = `runtime-${i}`, service = `assistant-${name}`, network = `assistant-${name}`;
    let slot = slots.find(slot => slot.name === name);
    if (!slot) {
      slot = { name, url: `http://${service}:18792`, controlToken: randomBytes(32).toString('base64url'),
        toolToken: randomBytes(32).toString('base64url'), confirmationToken: randomBytes(32).toString('base64url') };
      slots.push(slot);
    }
    const state = join(root, name); mkdirSync(state, { recursive: true, mode: 0o700 }); chmodSync(state, 0o700);
    const values = { ASSISTANT_RUNTIME_HOST: '0.0.0.0', ASSISTANT_RUNTIME_PORT: '18792', ASSISTANT_GATEWAY_PORT: '18791',
      ASSISTANT_RUNTIME_STATE_DIR: '/state', ASSISTANT_OPENCLAW_MODULE: '/opt/openclaw/node_modules/openclaw/openclaw.mjs',
      ASSISTANT_OPENCLAW_PLUGIN: '/app/runtime/plugin', ASSISTANT_PROMPT_FILE: '/app/runtime/AGENTS.md',
      ASSISTANT_BRIDGE_URL: 'http://assistant-control:18790',
      ASSISTANT_RUNTIME_CONTROL_TOKEN: slot.controlToken, ASSISTANT_RUNTIME_TOOL_TOKEN: slot.toolToken,
      ASSISTANT_RUNTIME_CONFIRMATION_TOKEN: slot.confirmationToken };
    writeFileSync(join(privateDir, `${name}.env`), Object.entries(values).map(([key, value]) => `${key}=${value}`).join('\n') + '\n', { mode: 0o600 });
    services[service] = { image: '${ASSISTANT_IMAGE:?}', command: ['dist/runtime/supervisor.js'],
      user: `${process.getuid!()}:${process.getgid!()}`, read_only: true, restart: 'unless-stopped',
      security_opt: ['no-new-privileges:true'], cap_drop: ['ALL'],
      mem_limit: '${ASSISTANT_RUNTIME_MEMORY:?}', cpus: '${ASSISTANT_RUNTIME_CPUS:?}',
      pids_limit: 128, env_file: [`./data/assistant/private/${name}.env`],
      healthcheck: { test: ['CMD', 'node', '-e',
        'fetch("http://127.0.0.1:18792/status",{headers:{Authorization:"Bearer "+process.env.ASSISTANT_RUNTIME_CONTROL_TOKEN}}).then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))'],
        interval: '15s', timeout: '5s', retries: 4 },
      volumes: [`./data/assistant/${name}:/state`], tmpfs: ['/tmp:rw,nosuid,nodev,noexec,size=128m'],
      networks: [network], logging: { driver: 'json-file', options: { 'max-size': '5m', 'max-file': '2' } } };
    networks[network] = {};
  }
  services['assistant-control'] = { user: `${process.getuid!()}:${process.getgid!()}`, networks: ['assistant-core', 'assistant-ingress', ...Object.keys(networks)] };
  writeFileSync(slotFile, JSON.stringify(slots), { mode: 0o600 });
  writeFileSync(join(root, 'runtime-compose.json'), JSON.stringify({ services, networks }, null, 2) + '\n', { mode: 0o600 });
  console.log(`Prepared ${count} private runtime slots; credentials were not printed.`);
} else throw new Error('Use prepare');
