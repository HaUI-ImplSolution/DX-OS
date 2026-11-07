// SPDX-License-Identifier: Apache-2.0
import { ApiError } from '../errors.js';
import { Store, type Owner, type Proposal } from '../storage/store.js';
import { CoreClient, targetPath, type Target } from './core-client.js';

type Effect = { operation: string; user: string; group?: string; groupField?: string; asynchronous: boolean };
export type Entity = { name: string; label: string; canRead: boolean; canAssign: boolean; canHandle?: boolean; modelVersion?: number;
  fields: { name: string; label: string; type: string }[];
  process?: { family: string; allowSelfDecision: boolean;
    states: { name: string; label: string; terminal: boolean }[];
    actions: { name: string; label: string; from: string[]; to: string; by: string; needsNote: boolean }[];
    effects?: Record<string, Effect[]> } };
export type Model = { name: string; modelVersion?: number; held: boolean; entities: Entity[] };
type RecordData = Record<string, unknown> & { version: number; status: string };
type Handler = { id: string; name: string; username?: string };

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

  async handlers(owner: Owner, target: Target): Promise<Handler[]> {
    const entity = await this.entity(owner, target);
    if (!entity.canAssign) throw new ApiError(403, 'ERR_ACTION_NOT_ALLOWED', 'You cannot assign this record');
    return this.core.read<Handler[]>(owner, `${targetPath(target)}/handlers`);
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

  async propose(owner: Owner, target: Target, input: {
    operation: 'action' | 'assign'; action?: string; assignee?: string | null; note?: string;
  }) {
    const { entity, record } = await this.record(owner, target);
    if (!Number.isSafeInteger(record.version) || record.version < 0 ||
        !Number.isSafeInteger(entity.modelVersion) || entity.modelVersion! < 1)
      throw new ApiError(409, 'ERR_CONDITIONAL_API_REQUIRED', 'Core must support conditional actions before the assistant can write');
    let label: string; let webOnly = false;
    let effectsSummary: NonNullable<Proposal['effects']> = [];
    if (input.operation === 'assign') {
      if (!entity.canAssign) throw new ApiError(403, 'ERR_ACTION_NOT_ALLOWED', 'You cannot assign this record');
      const candidates = await this.core.read<Handler[]>(owner, `${targetPath(target)}/handlers`);
      const person = candidates.find(person => person.id === input.assignee);
      if (input.assignee !== null && !person)
        throw new ApiError(400, 'ERR_ASSIGNEE', 'Select an eligible person from the handler list');
      label = person ? `Giao cho ${person.name}` : 'Trả việc về nhóm xử lý';
    } else {
      const action = entity.process?.actions.find(action => action.name === input.action);
      if (!action || !action.from.includes(record.status))
        throw new ApiError(409, 'ERR_INVALID_STATE', 'This action is not available in the current state', 'WRONG_STATE');
      if (action.needsNote && !input.note?.trim())
        throw new ApiError(400, 'ERR_NOTE_REQUIRED', 'Provide the note required for this action');
      const effects = entity.process?.effects;
      webOnly = !effects || (effects[action.to]?.length ?? 0) > 0;
      effectsSummary = (effects?.[action.to] ?? []).map(effect => ({
        operation: effect.operation, asynchronous: effect.asynchronous,
        userSource: effect.user === 'created_by' ? 'Người tạo hồ sơ' :
          entity.fields.find(field => field.name === effect.user)?.label ?? 'Trường người dùng của hồ sơ',
        group: effect.group ?? String(effect.groupField ? record[effect.groupField] ?? 'Chưa xác định' : 'Chưa xác định'),
      }));
      label = action.label;
    }
    const titleField = entity.fields.find(field => field.type === 'text');
    const title = String(titleField ? record[titleField.name] ?? entity.label : entity.label).slice(0, 280);
    const summary = `Mô hình: ${target.model.replace(/_/g, ' ')}\n${entity.label}\n${label}: ${title}${input.note ? `\nGhi chú: ${input.note}` : ''}`;
    const snapshot: Proposal = { model: target.model, entity: target.entity, recordId: target.id,
      operation: input.operation, action: input.action, assignee: input.assignee, note: input.note,
      version: record.version, modelVersion: entity.modelVersion!, summary, webOnly, effects: effectsSummary };
    const id = this.store.createProposal(owner, snapshot);
    return { proposalId: id, summary, webOnly, expires: this.store.proposal(owner, id).expires };
  }

  async execute(owner: Owner, id: string, channel: 'web' | 'telegram') {
    const proposal = this.store.proposal(owner, id);
    const target = { model: proposal.snapshot.model, entity: proposal.snapshot.entity, id: proposal.snapshot.recordId };
    await this.entity(owner, target);
    if (proposal.snapshot.operation === 'assign' && proposal.snapshot.assignee !== null) {
      const candidates = await this.handlers(owner, target);
      if (!candidates.some(person => person.id === proposal.snapshot.assignee))
        throw new ApiError(409, 'ERR_ASSIGNEE_CHANGED', 'The assignee is no longer eligible; create a new proposal');
    }
    const snapshot = this.store.claim(owner, id, channel);
    const suffix = snapshot.operation === 'assign' ? '/assign' : `/actions/${snapshot.action}`;
    try {
      const result = await this.core.write<RecordData>(owner, `${targetPath(target)}${suffix}`, {
        version: snapshot.version, modelVersion: snapshot.modelVersion,
        ...(snapshot.operation === 'assign' ? { assignee: snapshot.assignee } : { note: snapshot.note }),
      });
      this.store.finish(owner, id, 'succeeded', result);
      return result;
    } catch (error) {
      const uncertain = !(error instanceof ApiError) || error.errorCode === 'ERR_EXECUTION_UNCERTAIN' || error.status >= 500;
      this.store.finish(owner, id, uncertain ? 'uncertain' : 'failed', {
        errorCode: error instanceof ApiError ? error.errorCode : 'ERR_EXECUTION_UNCERTAIN',
        reason: error instanceof ApiError ? error.reason : undefined,
      });
      throw error;
    }
  }
}
