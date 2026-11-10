// SPDX-License-Identifier: Apache-2.0
import { ApiError } from '../errors.js';
import type { Owner } from '../storage/store.js';
import type { CoreClient } from './core-client.js';

const fields = (row: Record<string, unknown>, names: readonly string[]) => Object.fromEntries(
  names.filter(name => row[name] === null || ['string', 'number', 'boolean'].includes(typeof row[name]))
    .map(name => [name, row[name]]));

export async function administration(core: CoreClient, owner: Owner, operation: string, model?: string, yaml?: string) {
  // Every endpoint also checks the caller's ADMIN role in Core.
  if (operation === 'models') {
    const rows = await core.read<Record<string, unknown>[]>(owner, '/models');
    return rows.slice(0, 100).map(row => fields(row, ['model', 'name', 'version', 'held', 'status', 'applied_at']));
  }
  if (operation === 'runs') {
    if (!model || !/^[a-z][a-z0-9_]{0,39}$/.test(model)) throw new ApiError(400, 'ERR_TARGET', 'Choose an applied model');
    const rows = await core.read<Record<string, unknown>[]>(owner, `/models/${model}/runs`);
    return rows.slice(0, 20).map(row => fields(row, ['id', 'model', 'version', 'status', 'started_at', 'finished_at']));
  }
  if (operation === 'outbox') {
    const data = await core.read<{ counts: Record<string, number> }>(owner, '/outbox?status=failed&page=0&size=1');
    return { counts: fields(data.counts, ['pending', 'failed', 'stopped', 'delivered']), handoff: '/console' };
  }
  if (operation === 'audit') {
    const data = await core.read<{ items: Record<string, unknown>[] }>(owner, '/audit-logs?page=0&size=20');
    return data.items.map(row => fields(row, ['action', 'targetType', 'outcome', 'occurredAt']));
  }
  if (operation === 'plan' && yaml && yaml.length <= 20000) {
    const plan = await core.plan<Record<string, unknown> & { changes: Record<string, unknown>[];
      violations: Record<string, unknown>[]; rejections: unknown[]; questions: unknown[] }>(owner, yaml);
    return { ...fields(plan, ['model', 'fromVersion', 'version', 'action', 'level']),
      changes: plan.changes.map(row => fields(row, ['level', 'kind', 'entity', 'field'])),
      violations: plan.violations.map(row => fields(row, ['entity', 'field', 'rule', 'rows'])),
      rejectionCount: plan.rejections.length, questionCount: plan.questions.length, handoff: '/console',
      message: 'Chỉ là kế hoạch xem trước, chưa áp dụng. Mở Console để xem lý do đầy đủ; không suy đoán phần đã lược bỏ.' };
  }
  throw new ApiError(400, 'ERR_OPERATION', 'Choose a supported administrative read operation');
}
