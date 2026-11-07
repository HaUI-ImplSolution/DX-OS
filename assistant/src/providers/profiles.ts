// SPDX-License-Identifier: Apache-2.0
import { randomUUID } from 'node:crypto';
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';
import type { Runtimes } from '../runtime/slots.js';

export type Provider = 'gemini' | 'openai' | 'anthropic' | 'compatible';
export type Profile = { id: string; provider: Provider; model: string; apiKey: string; baseUrl?: string };
const providers = {
  gemini: { id: 'google', api: 'google-generative-ai', baseUrl: 'https://generativelanguage.googleapis.com/v1beta' },
  openai: { id: 'openai', api: 'openai-responses', baseUrl: 'https://api.openai.com/v1' },
  anthropic: { id: 'anthropic', api: 'anthropic-messages', baseUrl: 'https://api.anthropic.com' },
  compatible: { id: 'dxos_compatible', api: 'openai-completions', baseUrl: '' },
} as const;

export class Profiles {
  constructor(private readonly store: Store, private readonly compatibleUrls: readonly string[],
    private readonly runtimes: Pick<Runtimes, 'update'>) {}

  list(owner: Owner) {
    const saved = this.store.getSecret<{ profiles: Profile[]; selected?: string }>(owner, 'providers');
    return { selected: saved?.selected ?? null, profiles: (saved?.profiles ?? []).map(profile => ({
      id: profile.id, provider: profile.provider, model: profile.model,
      baseUrl: profile.baseUrl, configured: true,
    })) };
  }

  active(owner: Owner): Profile {
    const saved = this.store.getSecret<{ profiles: Profile[]; selected?: string }>(owner, 'providers');
    const profile = saved?.profiles.find(item => item.id === saved.selected);
    if (!profile) throw new ApiError(409, 'ERR_PROVIDER_REQUIRED', 'Select a provider profile first');
    return profile;
  }

  get(owner: Owner, id: string): Profile {
    const saved = this.store.getSecret<{ profiles: Profile[] }>(owner, 'providers');
    const profile = saved?.profiles.find(profile => profile.id === id);
    if (!profile) throw new ApiError(404, 'ERR_NOT_FOUND', 'Provider profile not found');
    return profile;
  }

  async save(owner: Owner, input: Omit<Profile, 'id'> & { id?: string }): Promise<string> {
    if (input.provider === 'compatible') {
      if (!input.baseUrl || !this.compatibleUrls.includes(input.baseUrl))
        throw new ApiError(400, 'ERR_PROVIDER_ORIGIN', 'This endpoint has not been allowed by an administrator');
      const url = new URL(input.baseUrl);
      if (url.protocol !== 'https:' || url.username || url.password || url.hash || url.search)
        throw new ApiError(400, 'ERR_PROVIDER_ORIGIN', 'Use an approved HTTPS endpoint');
    } else if (input.baseUrl) {
      throw new ApiError(400, 'ERR_PROVIDER_ORIGIN', 'The provider uses its published endpoint');
    }
    return this.runtimes.update(owner, () => {
      const saved = this.store.getSecret<{ profiles: Profile[]; selected?: string }>(owner, 'providers') ?? { profiles: [] };
      const id = input.id ?? randomUUID();
      if (input.id && !saved.profiles.some(item => item.id === id))
        throw new ApiError(404, 'ERR_NOT_FOUND', 'Provider profile not found');
      if (!input.id && saved.profiles.length >= 5)
        throw new ApiError(409, 'ERR_PROVIDER_LIMIT', 'At most five profiles can be saved');
      const profile = { ...input, id };
      saved.profiles = [...saved.profiles.filter(item => item.id !== id), profile];
      saved.selected ??= id;
      this.store.setSecret(owner, 'providers', saved);
      return id;
    });
  }

  async select(owner: Owner, id: string): Promise<void> {
    return this.runtimes.update(owner, () => {
      const saved = this.store.getSecret<{ profiles: Profile[]; selected?: string }>(owner, 'providers');
      if (!saved?.profiles.some(profile => profile.id === id))
        throw new ApiError(404, 'ERR_NOT_FOUND', 'Provider profile not found');
      saved.selected = id;
      this.store.setSecret(owner, 'providers', saved);
    });
  }

  async remove(owner: Owner, id: string): Promise<void> {
    return this.runtimes.update(owner, () => {
      const saved = this.store.getSecret<{ profiles: Profile[]; selected?: string }>(owner, 'providers');
      if (!saved?.profiles.some(profile => profile.id === id))
        throw new ApiError(404, 'ERR_NOT_FOUND', 'Provider profile not found');
      saved.profiles = saved.profiles.filter(profile => profile.id !== id);
      if (saved.selected === id) delete saved.selected;
      this.store.setSecret(owner, 'providers', saved);
    });
  }
}

export function modelConfiguration(profile: Profile) {
  const provider = providers[profile.provider];
  const model = profile.model.replace(new RegExp(`^${provider.id}/`), '');
  return { primary: `${provider.id}/${model}`, providers: {
    [provider.id]: { baseUrl: profile.baseUrl ?? provider.baseUrl, api: provider.api, timeoutSeconds: 60,
      apiKey: '${DXOS_PROVIDER_API_KEY}', request: { allowPrivateNetwork: false },
      models: [{ id: model, name: model, api: provider.api, maxTokens: 4096 }] },
  } };
}
