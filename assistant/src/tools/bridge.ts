// SPDX-License-Identifier: Apache-2.0
import type { FastifyInstance } from 'fastify';
import { ApiError } from '../errors.js';
import type { Runtimes } from '../runtime/slots.js';
import { targetPath, type CoreClient, type Target } from './core-client.js';
import type { Records } from './records.js';
import type { Store } from '../storage/store.js';
import type { Telegram } from '../accounts/telegram.js';
import { administration } from './administration.js';

type Input = Target & { tool: string; operation?: 'action' | 'assign'; action?: string;
  assignee?: string | null; note?: string; page?: number; size?: number;
  filters?: { field: string; op: string; value: string }[]; adminOperation?: string; yaml?: string };
const name = { type: 'string', pattern: '^[a-z][a-z0-9_]{0,39}$' };

export function bridgeRoutes(app: FastifyInstance, runtimes: Runtimes, records: Records, core: CoreClient, store: Store) {
  const budget = new Map<string, { start: number; count: number }>();
  app.post<{ Body: Input }>('/internal/v1/tools', {
    schema: { body: { type: 'object', required: ['tool'], additionalProperties: false, properties: {
      tool: { type: 'string', enum: ['models', 'inbox', 'record', 'list', 'events', 'stats', 'handlers', 'propose', 'admin'] },
      adminOperation: { type: 'string', enum: ['models', 'runs', 'outbox', 'audit', 'plan'] },
      yaml: { type: 'string', maxLength: 20000 },
      model: name, entity: name, id: { type: 'integer', minimum: 1, maximum: Number.MAX_SAFE_INTEGER },
      operation: { type: 'string', enum: ['action', 'assign'] }, action: name,
      assignee: { type: ['string', 'null'], maxLength: 255 },
      note: { type: 'string', maxLength: 2000 }, page: { type: 'integer', minimum: 0, maximum: 10000 },
      size: { type: 'integer', minimum: 1, maximum: 100 },
      filters: { type: 'array', maxItems: 6, items: { type: 'object', required: ['field', 'op', 'value'],
        additionalProperties: false, properties: { field: name,
          op: { type: 'string', enum: ['eq', 'ne', 'lt', 'lte', 'gt', 'gte', 'like', 'in'] },
          value: { type: 'string', maxLength: 500 } } } },
    } } },
  }, async request => {
    const owner = runtimes.authorize(request.headers.authorization?.replace(/^Bearer /, '') ?? '', 'tool');
    const key = JSON.stringify([owner.issuer, owner.subject]);
    let window = budget.get(key);
    if (!window || Date.now() - window.start >= 60000) { window = { start: Date.now(), count: 0 }; budget.set(key, window); }
    if (++window.count > 30) throw new ApiError(429, 'ERR_TOOL_BUDGET', 'The tool budget is exhausted; wait before trying again');
    const telegram = store.getSecret<Telegram>(owner, 'telegram');
    if (!telegram?.userId) throw new ApiError(403, 'ERR_PAIRING_REQUIRED', 'Pair Telegram before using tools');
    const input = request.body;
    let data: unknown;
    if (input.tool === 'models') data = await records.models(owner);
    else if (input.tool === 'admin') data = await administration(core, owner, input.adminOperation ?? '', input.model, input.yaml);
    else if (input.tool === 'inbox') {
      const result = await core.read<{ items: { model: string }[]; page: number; totalElements: number; totalPages: number }>(owner,
        `/inbox?page=${input.page ?? 0}&size=${input.size ?? 20}`);
      const byModel: Record<string, number> = {};
      for (const item of result.items) byModel[item.model] = (byModel[item.model] ?? 0) + 1;
      data = { ...result, retrievedAt: new Date().toISOString(), pageSummary: {
        returnedCount: result.items.length, returnedCountsByModel: byModel,
        allItemsReturned: result.items.length === result.totalElements,
        ...(result.items.length === 0 && result.totalElements > 0 ? {
          instruction: 'This page is empty, but the inbox is not empty. Read page 0 before answering.', suggestedPage: 0,
        } : {}),
      } };
    }
    else {
      const target = { model: input.model, entity: input.entity, id: input.id ?? 1 };
      const path = targetPath(target);
      if (input.tool === 'record') data = await records.record(owner, input);
      else if (input.tool === 'handlers') data = await records.handlers(owner, input);
      else if (input.tool === 'events') {
        const events = await core.read<unknown[]>(owner, `${targetPath(input)}/events`);
        data = { events: events.slice(-100), truncated: events.length > 100 };
      } else if (input.tool === 'propose') {
        if (!input.operation) throw new ApiError(400, 'ERR_OPERATION', 'Choose action or assign');
        data = await records.propose(owner, input, { ...input, operation: input.operation });
      } else {
        const entity = await records.entity(owner, target);
        const params = new URLSearchParams({ page: String(input.page ?? 0), size: String(input.size ?? 20) });
        for (const filter of input.filters ?? []) {
          if (!entity.fields.some(field => field.name === filter.field) &&
              !['status', 'assignee', 'created_by', 'handle_by', 'created_at', 'updated_at'].includes(filter.field))
            throw new ApiError(400, 'ERR_FILTER', 'Choose a field from the model metadata');
          params.set(`filter.${filter.field}.${filter.op}`, filter.value);
        }
        const entityPath = path.slice(0, path.lastIndexOf('/'));
        data = await core.read(owner, `${entityPath}${input.tool === 'stats' ? '/stats' : ''}?${params}`);
      }
    }
    return { success: true, message: 'OK', data, retrievedAt: new Date().toISOString() };
  });
  for (const operation of ['confirm', 'cancel'] as const) app.post<{ Body: { proposalId: string; userId: string; chatId: string } }>(`/internal/v1/${operation}`, {
    schema: { body: { type: 'object', required: ['proposalId', 'userId', 'chatId'], additionalProperties: false,
      properties: { proposalId: { type: 'string', maxLength: 64 }, userId: { type: 'string', pattern: '^[0-9]+$' },
        chatId: { type: 'string', pattern: '^[0-9]+$' } } } },
  }, async request => {
    const owner = runtimes.authorize(request.headers.authorization?.replace(/^Bearer /, '') ?? '', 'confirmation');
    const paired = store.getSecret<Telegram>(owner, 'telegram');
    if (!paired?.userId || paired.userId !== request.body.userId || paired.userId !== request.body.chatId)
      throw new ApiError(403, 'ERR_TELEGRAM_IDENTITY', 'The confirmation belongs to another chat');
    if (operation === 'cancel') {
      store.cancel(owner, request.body.proposalId);
      return { success: true, message: 'OK', data: null };
    }
    return { success: true, message: 'OK', data: await records.execute(owner, request.body.proposalId, 'telegram') };
  });
}
