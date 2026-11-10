// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { modelConfiguration, type Profile } from '../providers/profiles.js';

export function runtimeConfiguration(profile: Profile, userId: string | undefined,
  gatewayPort: number, workspace: string, pluginPath: string, telegramAccount = 'default') {
  const model = modelConfiguration(profile);
  const id = userId;
  return {
    gateway: { mode: 'local', bind: 'loopback', port: gatewayPort,
      auth: { mode: 'token', token: randomBytes(32).toString('base64url') } },
    models: { providers: model.providers },
    agents: { defaults: { workspace, heartbeat: { every: '0m' }, timeoutSeconds: 180,
      skipBootstrap: true, maxConcurrent: 1, model: { primary: model.primary, fallbacks: [] } } },
    channels: { telegram: { enabled: true, defaultAccount: telegramAccount, accounts: { [telegramAccount]: {} }, botToken: '${TELEGRAM_BOT_TOKEN}',
      dmPolicy: id ? 'allowlist' : 'pairing', ...(id ? { allowFrom: [id] } : {}),
      groupPolicy: 'disabled', capabilities: { inlineButtons: 'dm' },
      execApprovals: { enabled: !!id, approvers: id ? [id] : [], target: 'dm' } } },
    commands: { native: false, text: false, config: false, restart: false,
      ownerAllowFrom: id ? [`telegram:${id}`] : [] },
    approvals: { plugin: { enabled: !!id, mode: 'targets', targets: id ? [{ channel: 'telegram', to: id }] : [] } },
    tools: { codeMode: false, toolSearch: false, allow: ['dxos_models', 'dxos_inbox', 'dxos_record',
      'dxos_list', 'dxos_events', 'dxos_stats', 'dxos_handlers', 'dxos_propose', 'dxos_admin'] },
    skills: { allowBundled: [] },
    plugins: { slots: { memory: 'none' }, load: { paths: [pluginPath] },
      allow: ['telegram', profile.provider === 'compatible' ? 'openai' : model.primary.split('/')[0], 'dxos-assistant'],
      entries: { 'dxos-assistant': { enabled: !!id } } },
  };
}
