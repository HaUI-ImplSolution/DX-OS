// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { createHash } from 'node:crypto';
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';
import { Records, type AiTask, type Entity } from './records.js';
import { CoreClient, targetPath, type Target } from './core-client.js';
import type { Classifier } from '../classifier/index.js';
import { normalized } from '../classifier/features.js';

const REVIEW_CONCURRENCY = 5;
const SESSION_ERRORS = new Set(['ERR_CORE_LINK', 'ERR_LINK_IDENTITY', 'ERR_IDENTITY_UNAVAILABLE', 'ERR_IDENTITY_RESPONSE']);
const isClosed = (entity: Entity, status: string) => entity.process?.states.some(x => x.name === status && x.terminal);

function matchesClassifierLabels(field: Entity['fields'][number] | undefined, classifier: Classifier) {
  const options = new Set(field?.options);
  return field?.type === 'choice' && !field.multiple && options.size === classifier.labels.length &&
    classifier.labels.every(label => options.has(label));
}

function classify(classifier: Classifier, task: AiTask, record: Record<string, unknown>) {
  const inputs = task.inputFields.map(name => {
    const value = record[name];
    return typeof value === 'string' ? value : '';
  });
  const result = classifier.predict(inputs);
  if (result.label === null) return result;
  const normalizedInputs = inputs.map(normalized);
  if (result.evidence.length < 1 || result.evidence.length > 3 || result.evidence.some(phrase => {
    const match = normalized(phrase);
    return !match || !normalizedInputs.some(input => input.includes(match));
  })) throw new ApiError(422, 'ERR_CLASSIFIER_OUTPUT', 'Classifier evidence must come from the declared record fields');
  return result;
}

export class FieldProposals {
  constructor(private readonly records: Records, private readonly core: CoreClient,
    private readonly store: Store, private readonly classifiers: ReadonlyMap<string, Classifier> = new Map()) {}
  status() {
    return { configured: this.classifiers.size > 0, classifiers: [...this.classifiers.keys()].sort() };
  }

  async review(owner: Owner, targets: Target[]) {
    await this.core.checkSession(owner);
    const metadata = new Map<string, Promise<Entity>>();
    const reviewTarget = async (target: Target) => {
      const key = `${target.model}/${target.entity}`;
      let pending = metadata.get(key);
      if (!pending) {
        pending = this.records.entity(owner, target);
        metadata.set(key, pending);
      }
      const entity = await pending;
      if (!entity.canWrite) return [];
      const { record } = await this.records.record(owner, target, entity);
      if (isClosed(entity, record.status)) return [];
      return Object.entries(entity.aiTasks ?? {}).flatMap(([task, config]) => {
        const classifier = this.classifiers.get(config.classifier);
        const field = entity.fields.find(x => x.name === config.outputField);
        if (!classifier || !matchesClassifierLabels(field, classifier)) return [];
        const { label: suggested, probability, evidence } = classify(classifier, config, record);
        const value = record[config.outputField];
        const current = typeof value === 'string' ? value : null;
        return { ...target, task, field: config.outputField, current, suggested, evidence,
          ...(suggested === null ? {} : { confidence: probability }),
          mismatch: suggested !== null && suggested !== current };
      });
    };
    const queue = targets.entries();
    const results: Awaited<ReturnType<typeof reviewTarget>>[] = [];
    let skipped = 0;
    let sessionFailed = false;
    await Promise.all(Array.from({ length: Math.min(REVIEW_CONCURRENCY, targets.length) }, async () => {
      for (const [index, target] of queue) {
        if (sessionFailed) break;
        try { results[index] = await reviewTarget(target); }
        catch (error) {
          if (error instanceof ApiError && (error.status === 401 || SESSION_ERRORS.has(error.errorCode))) {
            sessionFailed = true;
            throw error;
          }
          skipped++;
        }
      }
    }));
    return { items: results.flat(), skipped };
  }

  private async context(owner: Owner, target: Target, taskName: string) {
    const data = await this.records.record(owner, target);
    const task = data.entity.aiTasks && Object.hasOwn(data.entity.aiTasks, taskName) ? data.entity.aiTasks[taskName] : undefined;
    if (!task || !data.entity.canWrite) throw new ApiError(403, 'ERR_TASK_UNAVAILABLE', 'This task is unavailable');
    if (!Number.isSafeInteger(data.record.version) || !Number.isSafeInteger(data.entity.modelVersion) || data.record.version < 0 || data.entity.modelVersion! < 1)
      throw new ApiError(409, 'ERR_CONDITIONAL_API_REQUIRED', 'Core must support conditional writes');
    if (isClosed(data.entity, data.record.status))
      throw new ApiError(409, 'ERR_INVALID_STATE', 'The record is closed', 'RECORD_CLOSED');
    const classifier = this.classifiers.get(task.classifier);
    // The revision covers the task and the weights, so a change to either retires pending proposals.
    const revision = createHash('sha256').update(JSON.stringify([task, classifier?.revision])).digest('hex');
    return { ...data, task, revision, classifier };
  }

  private validateValue(entity: Entity, task: AiTask, value: unknown): asserts value is string {
    const field = entity.fields.find(x => x.name === task.outputField);
    if (field?.type !== 'choice' || field.multiple || typeof value !== 'string' || !field.options?.includes(value))
      throw new ApiError(422, 'ERR_PROPOSAL_VALUE', 'The proposed value is outside the task schema');
  }

  async create(owner: Owner, target: Target, taskName: string) {
    const c = await this.context(owner, target, taskName);
    const { task } = c;
    if (!c.classifier)
      throw new ApiError(409, 'ERR_CLASSIFIER_UNAVAILABLE', `Classifier '${task.classifier}' is not ready`);
    if (!matchesClassifierLabels(c.entity.fields.find(x => x.name === task.outputField), c.classifier))
      throw new ApiError(409, 'ERR_CLASSIFIER_LABELS', `Options for '${task.outputField}' must match the labels of classifier '${task.classifier}'`);
    const { label, probability, evidence } = classify(c.classifier, task, c.record);
    if (label === null) return { insufficient: true, proposal: null };
    this.validateValue(c.entity, task, label);
    const proposalId = this.store.createProposal(owner, { model: c.target.model, entity: c.target.entity, recordId: c.target.id,
      operation: 'field', task: taskName, field: task.outputField, value: label, previousValue: c.record[task.outputField] ?? null,
      version: c.record.version, modelVersion: c.entity.modelVersion!, taskRevision: c.revision,
      summary: `${c.entity.label}: ${task.label}`, webOnly: true, evidence, confidence: probability,
    });
    return { insufficient: false, proposal: this.store.proposal(owner, proposalId) };
  }

  private async checked(owner: Owner, id: string) {
    const proposal = this.store.proposal(owner, id); const s = proposal.snapshot;
    if (s.operation !== 'field' || !s.task || !s.field || !s.taskRevision)
      throw new ApiError(410, 'ERR_PROPOSAL_RETIRED', 'This legacy proposal is read-only');
    const target = { model: s.model, entity: s.entity, id: s.recordId };
    const c = await this.context(owner, target, s.task);
    if (s.version !== c.record.version || s.modelVersion !== c.entity.modelVersion || s.taskRevision !== c.revision || s.field !== c.task.outputField)
      throw new ApiError(409, 'ERR_PROPOSAL_STALE', 'Create a proposal for the current record and task');
    return { ...c, proposal, target };
  }

  async revise(owner: Owner, id: string, value: string) {
    const c = await this.checked(owner, id);
    this.validateValue(c.entity, c.task, value);
    return this.store.proposal(owner, this.store.revise(owner, id, value));
  }

  async execute(owner: Owner, id: string) {
    const c = await this.checked(owner, id);
    this.validateValue(c.entity, c.task, c.proposal.snapshot.value);
    if (c.proposal.snapshot.value === c.record[c.task.outputField])
      throw new ApiError(409, 'ERR_PROPOSAL_NO_CHANGE', 'The proposed value already matches the record');
    const s = this.store.claim(owner, id);
    try {
      const result = await this.core.patch<Record<string, unknown>>(owner, targetPath(c.target), {
        [s.field!]: s.value, version: s.version, modelVersion: s.modelVersion,
      });
      if (!Number.isSafeInteger(result.version) || (result.version as number) <= s.version)
        throw new ApiError(503, 'ERR_EXECUTION_UNCERTAIN', 'Core did not confirm an advanced record version');
      this.store.finish(owner, id, 'succeeded', result);
      return { coreCommitted: true, changed: true, record: result };
    } catch (error) {
      const uncertain = !(error instanceof ApiError) || error.errorCode === 'ERR_EXECUTION_UNCERTAIN' || error.status >= 500;
      this.store.finish(owner, id, uncertain ? 'uncertain' : 'failed', {
        errorCode: error instanceof ApiError ? error.errorCode : 'ERR_EXECUTION_UNCERTAIN',
      });
      throw error;
    }
  }
}
