// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';
import { CoreClient, targetPath, type Target } from './core-client.js';

type Effect = { operation: string; user: string; group?: string; groupField?: string; asynchronous: boolean };
export type Entity = { name: string; label: string; canRead: boolean; canWrite?: boolean; aiTasks?: Record<string, AiTask>; canAssign: boolean; canHandle?: boolean; handlerActions?: string[]; modelVersion?: number;
  fields: { name: string; label: string; type: string; options?: string[]; multiple?: boolean }[];
  process?: { family: string; allowSelfDecision: boolean;
    states: { name: string; label: string; terminal: boolean }[];
    actions: { name: string; label: string; from: string[]; to: string; by: string; needsNote: boolean }[];
    effects?: Record<string, Effect[]> } };
export type Model = { name: string; modelVersion?: number; held: boolean; entities: Entity[] };
type RecordData = Record<string, unknown> & { version: number; status: string };
export type AiTask = { label: string; inputFields: string[]; outputField: string; instruction: string;
  sources: string[]; maxLength: number };

export class Records {
  constructor(private readonly core: CoreClient, private readonly store: Store) {}

  async models(owner: Owner): Promise<Model[]> {
    return this.core.read<Model[]>(owner, '/meta/models');
  }

  async entity(owner: Owner, target: Target): Promise<Entity> {
    targetPath(target);
    const entity = await this.core.read<Entity>(owner,
      `/meta/models/${target.model}/entities/${target.entity}`);
    if (!entity.canRead) throw new ApiError(403, 'ERR_ACTION_NOT_ALLOWED', 'This model or entity is not available');
    return entity;
  }

  async record(owner: Owner, target: Target) {
    const entity = await this.entity(owner, target);
    const record = await this.core.read<RecordData>(owner, targetPath(target));
    return { target, entity, record, retrievedAt: new Date().toISOString() };
  }

  async proposal(owner: Owner, id: string) {
    const saved = this.store.proposal(owner, id);
    await this.record(owner, { model: saved.snapshot.model, entity: saved.snapshot.entity, id: saved.snapshot.recordId });
    return saved;
  }

  async proposals(owner: Owner, page: number, size: number) {
    const saved = this.store.proposals(owner, page, size);
    const visible = await Promise.all(saved.map(async proposal => {
      try { return await this.proposal(owner, proposal.id); }
      catch (error) {
        if (!(error instanceof ApiError) || ![403, 404].includes(error.status)) throw error;
        return null;
      }
    }));
    return visible.filter(proposal => proposal !== null);
  }

}
