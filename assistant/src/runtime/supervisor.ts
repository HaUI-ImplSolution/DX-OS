// SPDX-License-Identifier: Apache-2.0
import Fastify from 'fastify';
import { spawn, execFile, type ChildProcess } from 'node:child_process';
import { promisify } from 'node:util';
import { timingSafeEqual, randomBytes } from 'node:crypto';
import { mkdirSync, writeFileSync, readFileSync, mkdtempSync, existsSync, rmSync, renameSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import type { Profile } from '../providers/profiles.js';
import { runtimeConfiguration } from './configuration.js';
import { modelConfiguration } from '../providers/profiles.js';
import { ApiError } from '../errors.js';
import { waitForCapability } from './probe.js';

const required = (name: string) => {
  const value = process.env[name];
  if (!value) throw new Error(`Missing configuration: ${name}`);
  return value;
};
const dir = required('ASSISTANT_RUNTIME_STATE_DIR');
if (!isAbsolute(dir) || dir === '/') throw new Error('Use a private runtime state directory');
const module = required('ASSISTANT_OPENCLAW_MODULE');
const bridgeUrl = required('ASSISTANT_BRIDGE_URL');
const controlToken = required('ASSISTANT_RUNTIME_CONTROL_TOKEN');
const toolToken = required('ASSISTANT_RUNTIME_TOOL_TOKEN');
const confirmationToken = required('ASSISTANT_RUNTIME_CONFIRMATION_TOKEN');
const gatewayPort = Number(required('ASSISTANT_GATEWAY_PORT'));
if (!Number.isInteger(gatewayPort) || gatewayPort < 1 || gatewayPort > 65535)
  throw new Error('Invalid private gateway port');
mkdirSync(dir, { recursive: true, mode: 0o700 });
const configPath = join(dir, 'openclaw.json');
const stateDir = join(dir, 'state');
const workspace = join(dir, 'workspace');
mkdirSync(workspace, { recursive: true, mode: 0o700 });
let child: ChildProcess | undefined;
let environment: NodeJS.ProcessEnv | undefined;
let busy = false;
const execute = promisify(execFile);
function bindOwner(owner: { issuer: string; subject: string }) {
  if (!owner?.issuer || !owner.subject) throw new ApiError(400, 'ERR_RUNTIME_OWNER', 'Missing runtime owner');
  const path = join(dir, 'owner.json');
  if (existsSync(path)) {
    const saved = JSON.parse(readFileSync(path, 'utf8')) as { issuer: string; subject: string };
    if (saved.issuer !== owner.issuer || saved.subject !== owner.subject)
      throw new ApiError(403, 'ERR_RUNTIME_OWNER', 'This runtime belongs to another account');
  } else writeFileSync(path, JSON.stringify(owner), { mode: 0o600, flag: 'wx' });
}

async function stop() {
  if (!child) return;
  const running = child;
  if (running.exitCode !== null || running.signalCode !== null) { child = undefined; environment = undefined; return; }
  await new Promise<void>(resolve => {
    const timer = setTimeout(() => { if (running.pid) process.kill(-running.pid, 'SIGKILL'); }, 10000);
    running.once('exit', () => { clearTimeout(timer); resolve(); });
    if (running.pid) process.kill(-running.pid, 'SIGTERM');
  });
  child = undefined;
  environment = undefined;
}

const app = Fastify({ logger: false, bodyLimit: 32768 });
app.setErrorHandler((error, _request, reply) => {
  return reply.code(error instanceof ApiError ? error.status : 500).send({
    errorCode: error instanceof ApiError ? error.errorCode : 'ERR_RUNTIME',
    message: error instanceof ApiError ? error.message : 'The runtime could not complete the operation',
  });
});
app.addHook('onRequest', async request => {
  const supplied = Buffer.from(request.headers.authorization?.replace(/^Bearer /, '') ?? '');
  const expected = Buffer.from(controlToken);
  if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected))
    throw new ApiError(401, 'ERR_UNAUTHORIZED', 'Invalid runtime capability');
});
app.get('/status', async () => ({ active: !!child && child.exitCode === null && child.signalCode === null }));
app.post('/stop', async () => {
  if (busy) throw new ApiError(409, 'ERR_RUNTIME_BUSY', 'The runtime is busy');
  busy = true;
  try { await stop(); return { active: false }; } finally { busy = false; }
});
app.post<{ Body: { owner: { issuer: string; subject: string }; profile: Profile;
  telegram: { token: string; accountId?: string; userId?: string } } }>('/start', async request => {
  if (busy) throw new ApiError(409, 'ERR_RUNTIME_BUSY', 'The runtime is busy');
  busy = true;
  try {
    const supplied = request.body;
    bindOwner(supplied.owner);
    if (!supplied.telegram?.token || !supplied.profile?.apiKey ||
        !['gemini', 'openai', 'anthropic', 'compatible'].includes(supplied.profile.provider) ||
        !/^[\w./:-]{1,160}$/.test(supplied.profile.model))
      throw new ApiError(400, 'ERR_RUNTIME_CONFIG', 'The runtime configuration is incomplete');
    await stop();
    const id = supplied.telegram.userId;
    const botId = supplied.telegram.token.split(':')[0] ?? '';
    if (!/^[0-9]{5,20}$/.test(botId)) throw new ApiError(400, 'ERR_RUNTIME_CONFIG', 'Invalid Telegram bot identity');
    const account = supplied.telegram.accountId ?? `bot-${botId}`;
    if (!/^[a-z0-9-]{1,128}$/.test(account)) throw new ApiError(400, 'ERR_RUNTIME_CONFIG', 'Invalid Telegram account scope');
    const config = runtimeConfiguration(supplied.profile, id, gatewayPort, workspace, required('ASSISTANT_OPENCLAW_PLUGIN'), account);
    writeFileSync(configPath, JSON.stringify(config), { mode: 0o600 });
    writeFileSync(join(workspace, 'AGENTS.md'), readFileSync(required('ASSISTANT_PROMPT_FILE')), { mode: 0o600 });
    // Preserve stock onboarding templates outside the model context; keep user-edited files.
    const templates = join(module, '..', 'docs', 'reference', 'templates');
    const normalize = (text: string) => text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim();
    for (const name of ['BOOTSTRAP.md', 'IDENTITY.md', 'SOUL.md', 'USER.md']) {
      const current = join(workspace, name), stock = join(templates, name);
      if (existsSync(current) && existsSync(stock) &&
          normalize(readFileSync(current, 'utf8')) === normalize(readFileSync(stock, 'utf8'))) {
        const archive = join(dir, 'onboarding-archive'); mkdirSync(archive, { recursive: true, mode: 0o700 });
        renameSync(current, join(archive, name));
      }
    }
    environment = { PATH: process.env.PATH, HOME: dir, XDG_CACHE_HOME: join(dir, 'cache'), NODE_OPTIONS: '--max-old-space-size=512', OPENCLAW_CONFIG_PATH: configPath,
      OPENCLAW_STATE_DIR: stateDir, DXOS_PROVIDER_API_KEY: supplied.profile.apiKey,
      TELEGRAM_BOT_TOKEN: supplied.telegram.token, DXOS_BRIDGE_URL: bridgeUrl,
      DXOS_TOOL_TOKEN: toolToken, DXOS_CONFIRMATION_TOKEN: confirmationToken,
      DXOS_TELEGRAM_USER: id ?? '' };
    child = spawn(process.execPath, [module, 'gateway', '--port', String(gatewayPort)], {
      env: environment, cwd: dir, stdio: 'ignore', detached: true,
    });
    child.on('error', () => { child = undefined; environment = undefined; });
    return { active: true, starting: true };
  } finally { busy = false; }
});
app.post<{ Body: { code: string } }>('/pairing', async request => {
  if (busy || !child || !environment) throw new ApiError(409, 'ERR_RUNTIME_BUSY', 'Start the private runtime first');
  if (environment.DXOS_TELEGRAM_USER) throw new ApiError(409, 'ERR_ALREADY_PAIRED', 'The runtime is already paired');
  if (!/^[A-Z0-9]{6,12}$/.test(request.body.code)) throw new ApiError(400, 'ERR_PAIRING_CODE', 'Invalid pairing code');
  busy = true;
  try {
    const account = JSON.parse(readFileSync(configPath, 'utf8')).channels.telegram.defaultAccount;
    const options = { env: environment, timeout: 15000, maxBuffer: 65536 };
    const list = await execute(process.execPath, [module, 'pairing', 'list', 'telegram', '--account', account, '--json'], options);
    const pending = JSON.parse(list.stdout) as { requests: { id: string; code: string }[] };
    const entry = pending.requests.find(entry => entry.code === request.body.code);
    if (!entry || !/^[0-9]+$/.test(String(entry.id))) throw new ApiError(400, 'ERR_PAIRING_CODE', 'Pairing code is expired or invalid');
    await execute(process.execPath, [module, 'pairing', 'approve', 'telegram', request.body.code, '--account', account], options);
    await stop();
    return { userId: String(entry.id) };
  } finally { busy = false; }
});
app.post<{ Body: { owner: { issuer: string; subject: string }; profile: Profile } }>('/probe', async request => {
  if (busy) throw new ApiError(409, 'ERR_RUNTIME_BUSY', 'The runtime is busy');
  bindOwner(request.body.owner);
  const profile = request.body.profile;
  if (!profile?.apiKey || !['gemini', 'openai', 'anthropic', 'compatible'].includes(profile.provider))
    throw new ApiError(400, 'ERR_RUNTIME_CONFIG', 'Invalid provider configuration');
  busy = true;
  const probeDir = mkdtempSync(join(dir, 'probe-'));
  const marker = join(probeDir, 'called');
  const started = Date.now();
  try {
    const model = modelConfiguration(profile);
    const config = { gateway: { mode: 'local' }, models: { providers: model.providers },
      agents: { defaults: { workspace: probeDir, model: { primary: model.primary, fallbacks: [] },
        heartbeat: { every: '0m' }, skipBootstrap: true, timeoutSeconds: 60 } },
      tools: { codeMode: false, allow: ['dxos_probe'], toolSearch: false },
      plugins: { slots: { memory: 'none' }, load: { paths: [required('ASSISTANT_OPENCLAW_PLUGIN')] },
        allow: [profile.provider === 'compatible' ? 'openai' : model.primary.split('/')[0], 'dxos-assistant'],
        entries: { 'dxos-assistant': { enabled: true } } } };
    const path = join(probeDir, 'config.json');
    writeFileSync(path, JSON.stringify(config), { mode: 0o600 });
    const probe = spawn(process.execPath, [module, 'agent', '--local', '--session-id', randomBytes(16).toString('hex'),
      '--message', 'This is a synthetic capability check. Call dxos_probe with no arguments once, then say OK.', '--json'], {
      env: { PATH: process.env.PATH, HOME: probeDir, XDG_CACHE_HOME: join(probeDir, 'cache'),
        NODE_OPTIONS: '--max-old-space-size=512',
        OPENCLAW_CONFIG_PATH: path, OPENCLAW_STATE_DIR: join(probeDir, 'state'),
        DXOS_PROVIDER_API_KEY: profile.apiKey, DXOS_PROBE_MARKER: marker },
      stdio: 'ignore', detached: true,
    });
    if (!await waitForCapability(probe, marker))
      throw new ApiError(422, 'ERR_TOOL_CAPABILITY', 'The model check did not complete the required tool call');
    return { toolCalling: true, model: profile.model, provider: profile.provider, durationMs: Date.now() - started };
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(503, 'ERR_PROVIDER_PROBE', 'The provider check failed; check the key, model, quota and connection');
  } finally { rmSync(probeDir, { recursive: true, force: true }); busy = false; }
});
for (const signal of ['SIGTERM', 'SIGINT'] as const) process.once(signal, () => {
  void stop().then(() => app.close());
});
await app.listen({ host: required('ASSISTANT_RUNTIME_HOST'), port: Number(required('ASSISTANT_RUNTIME_PORT')) });
