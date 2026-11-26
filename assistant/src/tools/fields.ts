// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { createHash } from 'node:crypto';
import { ApiError } from '../errors.js';
import { Store, type Owner, type Proposal } from '../storage/store.js';
import { Records, type AiTask, type Entity } from './records.js';
import { CoreClient, targetPath, type Target } from './core-client.js';
import { LocalInference } from '../inference/local.js';
import type { Classifier } from '../classifier/index.js';
import { normalized } from '../classifier/features.js';

export type Source = { id: string; title: string; revision: string; text: string; license: string };
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
  })) throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'Classifier evidence must come from the declared record fields');
  return result;
}

export class FieldProposals {
  private readonly sources: Map<string, Source>;
  constructor(private readonly records: Records, private readonly core: CoreClient,
    private readonly store: Store, private readonly inference: LocalInference | undefined, sources: Source[],
    private readonly classifiers: ReadonlyMap<string, Classifier> = new Map()) {
    if (!Array.isArray(sources) || sources.length > 100 || sources.some(x => !x ||
        typeof x.id !== 'string' || !/^[a-z][a-z0-9_]{0,39}$/.test(x.id) || typeof x.title !== 'string' || !x.title.trim() ||
        typeof x.revision !== 'string' || !x.revision.trim() || typeof x.text !== 'string' ||
        !x.text.trim() || x.text.length > 12000 || !['CC0-1.0','CC-BY-4.0','ODbL-1.0'].includes(x.license)) ||
        new Set(sources.map(x => x.id)).size !== sources.length) throw new Error('Invalid source catalog');
    this.sources = new Map(sources.map(x => [x.id, Object.freeze({ ...x })]));
  }
  status() {
    return { configured: !!this.inference || this.classifiers.size > 0, llmConfigured: !!this.inference,
      classifiers: [...this.classifiers.keys()].sort() };
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
        if (config.engine !== 'classifier') return [];
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

  private async context(owner: Owner, target: Target, taskName: string, creating = false) {
    const data = await this.records.record(owner, target);
    const task = data.entity.aiTasks && Object.hasOwn(data.entity.aiTasks, taskName) ? data.entity.aiTasks[taskName] : undefined;
    if (creating && task?.engine !== 'classifier' && !this.inference)
      throw new ApiError(409, 'ERR_INFERENCE_REQUIRED', 'Configure self-host inference first');
    if (!task || !data.entity.canWrite) throw new ApiError(403, 'ERR_TASK_UNAVAILABLE', 'This task is unavailable');
    if (!Number.isSafeInteger(data.record.version) || !Number.isSafeInteger(data.entity.modelVersion) || data.record.version < 0 || data.entity.modelVersion! < 1)
      throw new ApiError(409, 'ERR_CONDITIONAL_API_REQUIRED', 'Core must support conditional writes');
    if (isClosed(data.entity, data.record.status))
      throw new ApiError(409, 'ERR_INVALID_STATE', 'The record is closed', 'RECORD_CLOSED');
    const sources = (task.sources ?? []).map(id => {
      const source = this.sources.get(id);
      if (!source) throw new ApiError(409, 'ERR_SOURCE_UNAVAILABLE', 'A configured source is unavailable');
      return source;
    });
    const classifier = task.engine === 'classifier' ? this.classifiers.get(task.classifier) : undefined;
    const { engine, classifier: _name, ...taskFields } = task;
    // The LLM revision hashes the task fields, sources, and inference revision.
    const revisionInputs = engine === 'classifier'
      ? ['field-proposal-classifier-v1', task, classifier?.revision]
      : ['field-proposal-v4', taskFields, sources, this.inference?.revision];
    const revision = createHash('sha256').update(JSON.stringify(revisionInputs)).digest('hex');
    return { ...data, task, sources, revision, classifier };
  }

  private validateValue(entity: Entity, task: AiTask, value: unknown): asserts value is string {
    const field = entity.fields.find(x => x.name === task.outputField);
    if (!field || field.multiple || !['text','choice'].includes(field.type) || typeof value !== 'string' ||
        !value.trim() || (task.engine === 'classifier' ? field.type !== 'choice' : [...value].length > task.maxLength) ||
        (field.type === 'choice' && !field.options?.includes(value)))
      throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'The proposed value is outside the task schema');
  }

  async create(owner: Owner, target: Target, taskName: string, signal?: AbortSignal) {
    const c = await this.context(owner, target, taskName, true);
    const { task } = c;
    const field = c.entity.fields.find(x => x.name === task.outputField);
    if (task.engine === 'classifier') {
      if (!c.classifier)
        throw new ApiError(409, 'ERR_CLASSIFIER_UNAVAILABLE', `Classifier '${task.classifier}' is not ready`);
      if (!matchesClassifierLabels(field, c.classifier))
        throw new ApiError(409, 'ERR_CLASSIFIER_LABELS', `Options for '${task.outputField}' must match the labels of classifier '${task.classifier}'`);
      const { label, probability, evidence } = classify(c.classifier, task, c.record);
      if (label === null) return { insufficient: true, proposal: null };
      return this.save(owner, taskName, c, label, { sources: [], evidence, confidence: probability });
    }
    if (!field) throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'The proposed field is outside the task schema');
    const reviewFields = c.entity.fields.filter(f => task.inputFields.includes(f.name) &&
      f.name !== field.name && !f.multiple && ['choice','unit'].includes(f.type) && f.options?.length);
    const checks = Object.fromEntries(reviewFields.map(f => [f.name, { enum: [...f.options!, null] }]));
    const properties = {
      status: {}, value: {}, sources: { type: 'array', minItems: 1, maxItems: c.sources.length,
        items: { type: 'string', enum: c.sources.map(x => x.id) } },
      checks: { type: 'object', additionalProperties: false, required: reviewFields.map(f => f.name), properties: checks },
    };
    const schema = { oneOf: [
      { type: 'object', additionalProperties: false, required: ['status','value','sources','checks'], properties: {
        ...properties, status: { const: 'insufficient' }, value: { type: 'null' },
        checks: { ...properties.checks, properties: Object.fromEntries(reviewFields.map(f => [f.name, { type: 'null' }])) },
      } },
      { type: 'object', additionalProperties: false, required: ['status','value','sources','checks'], properties: {
        ...properties, status: { const: 'proposed' }, value: field.type === 'choice' ? { enum: field.options }
          : { type: 'string', minLength: 1, maxLength: task.maxLength },
      } },
    ] };
    const numericFields = c.entity.fields.filter(f => task.inputFields.includes(f.name) &&
      f.type === 'number' && Number.isSafeInteger(c.record[f.name]));
    const numericRelations = numericFields.flatMap((left, i) => numericFields.slice(i + 1).map(right => {
      const a = c.record[left.name] as number, b = c.record[right.name] as number;
      return [left.name, a === b ? '=' : a > b ? '>' : '<', right.name];
    }));
    const result = await this.inference!.suggest(task.instruction, {
      record: Object.fromEntries(task.inputFields.filter(name => !reviewFields.some(f => f.name === name)).map(name => [name, c.record[name] ?? null])),
      fields: Object.fromEntries(task.inputFields.map(name => [name, c.entity.fields.find(f => f.name === name)?.label ?? name])),
      numericRelations,
      checks: reviewFields.map(f => ({ field: f.name, label: f.label, options: f.options })),
      output: { label: field.label, options: field.options, maxLength: task.maxLength }, sources: c.sources,
    }, schema, signal) as { status?: unknown; value?: unknown; sources?: unknown; checks?: unknown } | null;
    if (!result || !['proposed','insufficient'].includes(String(result.status)) || Object.keys(result).some(key => !['status','value','sources','checks'].includes(key)) ||
        !Array.isArray(result.sources) || !result.sources.length || result.sources.length > c.sources.length ||
        result.sources.some(id => typeof id !== 'string' || !task.sources.includes(id)) ||
        new Set(result.sources).size !== result.sources.length)
      throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'Inference did not cite valid supplied sources');
    if (!result.checks || typeof result.checks !== 'object' || Array.isArray(result.checks) ||
        Object.keys(result.checks).length !== reviewFields.length ||
        Object.keys(result.checks).some(name => !reviewFields.some(f => f.name === name)))
      throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'Inference returned invalid record checks');
    const checked = result.checks as Record<string, unknown>;
    const warnings: { field: string; expected: string; actual: string | null }[] = [];
    for (const f of reviewFields) {
      const expected = checked[f.name];
      if (expected !== null && (typeof expected !== 'string' || !f.options!.includes(expected)))
        throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'A record check is outside the field options');
      if (result.status === 'insufficient' && expected !== null)
        throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'Insufficient evidence cannot propose record checks');
      if (typeof expected === 'string' && expected !== c.record[f.name])
        warnings.push({ field: f.name, expected, actual: typeof c.record[f.name] === 'string' ? c.record[f.name] as string : null });
    }
    if (result.status === 'insufficient') {
      if (result.value !== null) throw new ApiError(422, 'ERR_INFERENCE_OUTPUT', 'Insufficient evidence must not propose a value');
      return { insufficient: true, proposal: null };
    }
    return this.save(owner, taskName, c, result.value, {
      warnings,
      sources: c.sources.filter(x => (result.sources as string[]).includes(x.id)),
    });
  }

  private async save(owner: Owner, taskName: string, c: Awaited<ReturnType<FieldProposals['context']>>,
    value: unknown, details: Pick<Proposal, 'sources' | 'warnings' | 'evidence' | 'confidence'>) {
    this.validateValue(c.entity, c.task, value);
    const target = c.target;
    // Refresh after inference; any changed record or model needs a new decision.
    const current = await this.context(owner, target, taskName);
    if (current.record.version !== c.record.version || current.entity.modelVersion !== c.entity.modelVersion || current.revision !== c.revision)
      throw new ApiError(409, 'ERR_PROPOSAL_STALE', 'The task or record changed during inference');
    const proposalId = this.store.createProposal(owner, { model: target.model, entity: target.entity, recordId: target.id, operation: 'field',
      task: taskName, field: c.task.outputField, value, previousValue: c.record[c.task.outputField] ?? null,
      version: c.record.version, modelVersion: c.entity.modelVersion!, taskRevision: c.revision,
      summary: `${c.entity.label}: ${c.task.label}`, webOnly: true,
      ...details,
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
