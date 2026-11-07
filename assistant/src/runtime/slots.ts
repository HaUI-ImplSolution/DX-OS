// SPDX-License-Identifier: Apache-2.0
import { timingSafeEqual } from 'node:crypto';
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';

export type Slot = { name: string; url: string;
  controlToken: string; toolToken: string; confirmationToken: string };
export type RuntimeSettings = { profile: unknown; telegram: unknown };
const sameToken = (a: string, b: string) => {
  const left = Buffer.from(a); const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
};

export class Runtimes {
  private readonly pending = new Map<string, Promise<unknown>>();
  constructor(private readonly slots: readonly Slot[],
    private readonly settings: (owner: Owner) => RuntimeSettings | Promise<RuntimeSettings>, private readonly store: Store) {
    if (slots.length > 3) throw new Error('At most three runtime slots are supported');
    if (new Set(slots.map(slot => slot.name)).size !== slots.length || new Set(slots.map(slot => slot.url)).size !== slots.length)
      throw new Error('Runtime names and endpoints must be unique');
    const tokens = slots.flatMap(slot => [slot.controlToken, slot.toolToken, slot.confirmationToken]);
    if (new Set(tokens).size !== tokens.length || tokens.some(token => token.length < 32))
      throw new Error('Runtime capabilities must be distinct and contain at least 32 characters');
    for (const slot of slots) {
      if (!/^[a-z][a-z0-9-]{0,30}$/.test(slot.name)) throw new Error('Invalid slot name');
      const url = new URL(slot.url);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash)
        throw new Error('Invalid runtime endpoint');
    }
  }

  private slot(owner: Owner): Slot {
    const name = this.store.runtimeSlot(owner);
    const slot = this.slots.find(slot => slot.name === name);
    if (!slot) throw new ApiError(409, 'ERR_RUNTIME_REQUIRED', 'Ask an operator to provision your private runtime');
    return slot;
  }

  private serial<T>(owner: Owner, run: () => Promise<T>): Promise<T> {
    const key = JSON.stringify([owner.issuer, owner.subject]);
    const previous = this.pending.get(key) ?? Promise.resolve();
    const task = previous.catch(() => {}).then(run);
    this.pending.set(key, task);
    void task.finally(() => { if (this.pending.get(key) === task) this.pending.delete(key); }).catch(() => {});
    return task;
  }

  private async call(slot: Slot, path: 'status' | 'start' | 'stop' | 'pairing' | 'probe', body?: unknown) {
    let response: Response;
    try {
      response = await fetch(new URL(`/${path}`, slot.url), { method: body === undefined ? 'GET' : 'POST',
        headers: { Authorization: `Bearer ${slot.controlToken}`, 'Content-Type': 'application/json' },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }), redirect: 'error',
        signal: AbortSignal.timeout(path === 'probe' ? 100000 : path === 'pairing' ? 45000 : 15000) });
    } catch { throw new ApiError(503, 'ERR_RUNTIME_UNAVAILABLE', 'The private runtime is unavailable'); }
    const text = await response.text();
    if (text.length > 4096) throw new ApiError(502, 'ERR_RUNTIME_RESPONSE', 'Invalid runtime response');
    if (!response.ok) {
      const value = JSON.parse(text) as { errorCode?: string };
      const messages: Record<string, string> = {
        ERR_TOOL_CAPABILITY: 'The model replied without the required tool call',
        ERR_PAIRING_CODE: 'The Telegram pairing code is invalid or expired',
        ERR_ALREADY_PAIRED: 'This runtime is already paired',
        ERR_PROVIDER_PROBE: 'The provider check failed; check the key, model, quota and connection',
        ERR_RUNTIME_BUSY: 'The private runtime is busy',
      };
      const message = value.errorCode ? messages[value.errorCode] : undefined;
      throw new ApiError(message ? response.status : 503,
        message ? value.errorCode! : 'ERR_RUNTIME_UNAVAILABLE', message ?? 'The runtime could not complete the operation');
    }
    return JSON.parse(text) as { active: boolean; starting?: boolean; userId?: string };
  }

  async status(owner: Owner) {
    const slot = this.slots.find(slot => slot.name === this.store.runtimeSlot(owner));
    if (!slot) return { provisioned: this.slots.some(slot => !this.store.runtimeOwner(slot.name)), active: false };
    return { provisioned: true, ...await this.call(slot, 'status') };
  }

  start(owner: Owner) {
    return this.serial(owner, async () => {
      const settings = await this.settings(owner);
      this.store.allocateRuntime(owner, this.slots.map(slot => slot.name));
      const slot = this.slot(owner);
      this.store.setSecret(owner, 'runtimeEnabled', true);
      return this.call(slot, 'start', { owner, ...settings });
    });
  }

  stop(owner: Owner) {
    return this.serial(owner, async () => {
      this.store.setSecret(owner, 'runtimeEnabled', false);
      const slot = this.slots.find(slot => slot.name === this.store.runtimeSlot(owner));
      return slot ? this.call(slot, 'stop', {}) : { active: false };
    });
  }

  update<T>(owner: Owner, change: () => T): Promise<T> {
    return this.serial(owner, async () => {
      this.store.setSecret(owner, 'runtimeEnabled', false);
      const slot = this.slots.find(slot => slot.name === this.store.runtimeSlot(owner));
      if (slot) await this.call(slot, 'stop', {});
      return change();
    });
  }

  authorize(token: string, capability: 'tool' | 'confirmation'): Owner {
    if (token.length > 256) throw new ApiError(401, 'ERR_UNAUTHORIZED', 'Invalid runtime capability');
    const slot = this.slots.find(slot => sameToken(token,
      capability === 'tool' ? slot.toolToken : slot.confirmationToken));
    if (!slot) throw new ApiError(401, 'ERR_UNAUTHORIZED', 'Invalid runtime capability');
    const owner = this.store.runtimeOwner(slot.name);
    if (!owner) throw new ApiError(401, 'ERR_UNAUTHORIZED', 'The runtime has not been assigned');
    return owner;
  }

  pair<T>(owner: Owner, code: string, save: (userId: string) => T): Promise<T> {
    return this.serial(owner, async () => {
      const result = await this.call(this.slot(owner), 'pairing', { code });
      if (!result.userId || !/^[0-9]+$/.test(result.userId))
        throw new ApiError(502, 'ERR_PAIRING_RESPONSE', 'The pairing response is invalid');
      this.store.setSecret(owner, 'runtimeEnabled', false);
      return save(result.userId);
    });
  }

  probe(owner: Owner, profile: unknown) {
    return this.serial(owner, () => {
      this.store.allocateRuntime(owner, this.slots.map(slot => slot.name));
      return this.call(this.slot(owner), 'probe', { owner, profile });
    });
  }

  capacity() { return { capacity: 3, provisioned: this.slots.length,
    assigned: this.slots.filter(slot => this.store.runtimeOwner(slot.name)).length }; }

  async resume() {
    for (const slot of this.slots) {
      const owner = this.store.runtimeOwner(slot.name);
      if (owner && this.store.getSecret(owner, 'runtimeEnabled') === true) await this.start(owner).catch(() => {});
    }
  }
}
