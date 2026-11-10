// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';
import { CoreClient, targetPath, type Target } from '../tools/core-client.js';
import type { Records } from '../tools/records.js';
import type { Telegram } from '../accounts/telegram.js';
import { recordDetails } from './format.js';

type Item = Target & { title: string; status: string; assignee: string | null; overdue: boolean };
type Entry = { target?: Target; kind: 'new' | 'overdue' | 'due_soon' | 'digest';
  state: 'pending' | 'sending' | 'sent' | 'failed' | 'uncertain' | 'obsolete';
  attempts: number; after: number; expires: number };
type State = { enabled: boolean; epoch: string; initialized: boolean; timeZone: string;
  digestHour: number; lastDigest?: string; lastScan?: string; error?: string;
  seen: Record<string, { status: string; assignee: string | null; overdue: boolean; near: boolean }>;
  entries: Record<string, Entry> };
type Page<T> = { items: T[]; totalPages: number };
const keyOf = (item: Target) => JSON.stringify([item.model, item.entity, item.id]);
const labels = { new: 'Công việc cần chú ý', overdue: 'Công việc đã quá hạn', due_soon: 'Công việc sắp đến hạn', digest: 'Tổng kết công việc' };

export class Notifications {
  private readonly scanning = new Set<string>();
  private readonly pending = new Map<string, Promise<unknown>>();
  constructor(private readonly store: Store, private readonly core: CoreClient,
    private readonly records: Records, private readonly publicUrl: URL) {}

  private serial<T>(owner: Owner, run: () => T | Promise<T>): Promise<T> {
    const key = JSON.stringify([owner.issuer, owner.subject]);
    const previous = this.pending.get(key) ?? Promise.resolve();
    const task = previous.catch(() => {}).then(run);
    this.pending.set(key, task);
    void task.finally(() => { if (this.pending.get(key) === task) this.pending.delete(key); }).catch(() => {});
    return task;
  }
  configure(owner: Owner, enabled: boolean, timeZone: string, digestHour: number) {
    return this.serial(owner, () => this.configureState(owner, enabled, timeZone, digestHour));
  }
  tick(owner: Owner) { return this.serial(owner, () => this.scan(owner)); }
  claim(owner: Owner, ref: string, authorize = false) { return this.serial(owner, () => this.prepare(owner, ref, authorize)); }
  ack(owner: Owner, ref: string, messageId?: string, status?: number, retryAfter?: number) {
    return this.serial(owner, () => this.ackDelivery(owner, ref, messageId, status, retryAfter));
  }
  action(owner: Owner, ref: string, action: string) { return this.serial(owner, () => this.notificationAction(owner, ref, action)); }

  private state(owner: Owner): State | undefined { return this.store.getSecret<State>(owner, 'notifications'); }
  status(owner: Owner) {
    const state = this.state(owner);
    return { enabled: state?.enabled ?? false, timeZone: state?.timeZone ?? 'Asia/Ho_Chi_Minh',
      digestHour: state?.digestHour ?? 8, lastScan: state?.lastScan ?? null, error: state?.error ?? null,
      pending: Object.values(state?.entries ?? {}).filter(entry => entry.state === 'pending').length,
      uncertain: Object.values(state?.entries ?? {}).filter(entry => entry.state === 'uncertain' || entry.state === 'sending').length };
  }
  private configureState(owner: Owner, enabled: boolean, timeZone: string, digestHour: number) {
    try { new Intl.DateTimeFormat('vi', { timeZone }).format(); }
    catch { throw new ApiError(400, 'ERR_TIMEZONE', 'Choose a valid time zone'); }
    if (enabled && (!this.store.getSecret<Telegram>(owner, 'telegram')?.userId || !this.store.getSecret(owner, 'core')))
      throw new ApiError(409, 'ERR_LINK_REQUIRED', 'Link DX-OS and Telegram before enabling notifications');
    const old = this.state(owner);
    const state: State = old ?? { enabled, timeZone, digestHour, epoch: '', initialized: false, seen: {}, entries: {} };
    Object.assign(state, { enabled, timeZone, digestHour, epoch: randomBytes(18).toString('base64url') });
    for (const entry of Object.values(state.entries)) {
      if (entry.state === 'sending') entry.state = 'uncertain';
      else if (entry.state === 'pending') entry.state = 'obsolete';
    }
    this.store.setSecret(owner, 'notifications', state);
    return this.status(owner);
  }
  private save(owner: Owner, state: State) {
    if (this.state(owner)?.epoch !== state.epoch)
      throw new ApiError(409, 'ERR_NOTIFICATION_CHANGED', 'Notification settings changed');
    this.store.setSecret(owner, 'notifications', state);
  }
  private queue(state: State, kind: Entry['kind'], target?: Target) {
    const ref = randomBytes(18).toString('base64url');
    state.entries[ref] = { target, kind, state: 'pending', attempts: 0, after: 0, expires: Date.now() + 86400000 };
  }

  private async collect(owner: Owner): Promise<{ items: Item[]; near: Set<string> }> {
    const started = Date.now(); let pages = 0;
    const readPages = async <T>(path: string): Promise<T[]> => {
      const items: T[] = [];
      for (let page = 0;; page++) {
        if (++pages > 20 || Date.now() - started > 20000)
          throw new ApiError(503, 'ERR_NOTIFICATION_BUDGET', 'The inbox exceeds the scan budget; sync is incomplete');
        const data = await this.core.read<Page<T>>(owner, `${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`);
        if (!Array.isArray(data.items) || !Number.isSafeInteger(data.totalPages))
          throw new ApiError(502, 'ERR_CORE_RESPONSE', 'Core returned an invalid page');
        items.push(...data.items);
        if (page + 1 >= data.totalPages) return items;
      }
    };
    const items = await readPages<Item>('/inbox');
    const near = new Set<string>();
    const now = new Date().toISOString(), soon = new Date(Date.now() + 900000).toISOString();
    const groups = new Map(items.map(item => [`${item.model}/${item.entity}`, item]));
    for (const item of groups.values()) {
      const path = targetPath(item).replace(/\/[0-9]+$/, '');
      const params = new URLSearchParams({ 'filter.handle_by.gte': now, 'filter.handle_by.lte': soon });
      const rows = await readPages<{ id: number }>(`${path}?${params}`);
      for (const row of rows) near.add(keyOf({ ...item, id: row.id }));
    }
    return { items, near };
  }

  private async scan(owner: Owner) {
    const key = JSON.stringify(owner);
    if (this.scanning.has(key)) return { deliveries: [] };
    const state = this.state(owner);
    if (!state?.enabled) return { deliveries: [] };
    this.scanning.add(key);
    try {
      const { items, near } = await this.collect(owner);
      for (const [ref, entry] of Object.entries(state.entries)) {
        if (entry.expires <= Date.now()) delete state.entries[ref];
        else if (entry.state === 'sending') entry.state = 'uncertain';
      }
      for (const item of items) {
        const key = keyOf(item), previous = state.seen[key];
        const close = near.has(key);
        if (state.initialized) {
          if (!previous || previous.status !== item.status || previous.assignee !== item.assignee) this.queue(state, 'new', item);
          else if (item.overdue && !previous.overdue) this.queue(state, 'overdue', item);
          else if (close && !previous.near) this.queue(state, 'due_soon', item);
        }
        state.seen[key] = { status: item.status, assignee: item.assignee, overdue: item.overdue, near: close };
      }
      if (Object.keys(state.seen).length > 5000) throw new ApiError(503, 'ERR_NOTIFICATION_BUDGET', 'The notification ledger needs review');
      const parts = Object.fromEntries(new Intl.DateTimeFormat('en', { timeZone: state.timeZone,
        year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23' })
        .formatToParts(new Date()).map(part => [part.type, part.value]));
      const today = `${parts.year}-${parts.month}-${parts.day}`;
      if (state.initialized && Number(parts.hour) >= state.digestHour && state.lastDigest !== today) {
        this.queue(state, 'digest'); state.lastDigest = today;
      } else if (!state.initialized && Number(parts.hour) >= state.digestHour) state.lastDigest = today;
      state.initialized = true; state.lastScan = new Date().toISOString(); delete state.error;
      this.save(owner, state);
      return { deliveries: Object.entries(state.entries).filter(([, entry]) => entry.state === 'pending' && entry.after <= Date.now())
        .slice(0, 5).map(([ref]) => ({ ref })) };
    } catch (error) {
      const current = this.state(owner);
      if (current?.epoch === state.epoch) { current.error = error instanceof ApiError ? error.message : 'Notification sync failed'; this.save(owner, current); }
      return { deliveries: [] };
    } finally { this.scanning.delete(key); }
  }

  private entry(owner: Owner, ref: string) {
    const state = this.state(owner), entry = state?.entries[ref];
    if (!state?.enabled || !entry || entry.expires <= Date.now())
      throw new ApiError(409, 'ERR_NOTIFICATION_EXPIRED', 'This notification is expired or disabled');
    return { state, entry };
  }
  private async prepare(owner: Owner, ref: string, authorize = false) {
    const { state, entry } = this.entry(owner, ref);
    if (entry.state !== 'pending' || entry.after > Date.now() || entry.attempts >= 3)
      throw new ApiError(409, 'ERR_NOTIFICATION_CLOSED', 'This notification was already handled');
    const telegram = this.store.getSecret<Telegram>(owner, 'telegram');
    if (!telegram?.userId) throw new ApiError(409, 'ERR_PAIRING_REQUIRED', 'Pair Telegram first');
    let text = labels[entry.kind];
    if (entry.target) {
      try {
        const context = await this.records.record(owner, entry.target);
        if (context.entity.process?.states.find(state => state.name === context.record.status)?.terminal)
          throw new ApiError(404, 'ERR_CLOSED', 'The record is closed');
        text += `\n\n${recordDetails(context.entity, context.record, state.timeZone)}`;
      } catch (error) {
        if (error instanceof ApiError && [403, 404].includes(error.status)) { entry.state = 'obsolete'; this.save(owner, state); }
        throw error;
      }
    } else {
      const { items } = await this.collect(owner);
      text += `\nĐang chờ: ${items.length}\nQuá hạn: ${items.filter(item => item.overdue).length}\nTính lúc: ${new Date().toISOString()}`;
    }
    this.save(owner, state);
    if (authorize) { entry.state = 'sending'; entry.attempts++; this.save(owner, state); }
    return { to: telegram.userId, text, presentation: entry.target ? { blocks: [{ type: 'buttons',
      buttons: [{ label: 'Xem chi tiết và thao tác', action: { type: 'callback', value: `dxos_notice:${ref}:view` } }] }] } : undefined };
  }
  private ackDelivery(owner: Owner, ref: string, messageId?: string, status?: number, retryAfter?: number) {
    const { state, entry } = this.entry(owner, ref);
    if (entry.state !== 'sending') throw new ApiError(409, 'ERR_NOTIFICATION_CLOSED', 'This delivery is not in flight');
    if (messageId) entry.state = 'sent';
    else if (status === 429 && entry.attempts < 3) {
      entry.state = 'pending'; entry.after = Date.now() + Math.max(1000, Math.min(300000, (retryAfter ?? 30) * 1000));
    } else entry.state = 'uncertain';
    this.save(owner, state);
  }
  private async notificationAction(owner: Owner, ref: string, action: string) {
    const { state, entry } = this.entry(owner, ref);
    if (!entry.target) throw new ApiError(400, 'ERR_NOTIFICATION_ACTION', 'Open a record notification');
    const { entity, record } = await this.records.record(owner, entry.target);
    this.save(owner, state);
    if (action === 'view') {
      const available = entity.process?.actions.filter(action => action.from.includes(record.status) &&
        (action.by === 'OWNER' ? record.created_by === owner.subject :
          entity.canHandle && (!entity.handlerActions || entity.handlerActions.includes(action.name)) &&
          (entity.process?.allowSelfDecision || record.created_by !== owner.subject) &&
          (!record.assignee || record.assignee === owner.subject))) ?? [];
      return { text: recordDetails(entity, record, state.timeZone), presentation: { blocks: [{ type: 'buttons',
        buttons: available.map(action => ({ label: action.label,
          action: { type: 'callback', value: `dxos_notice:${ref}:${action.name}` } })) }] } };
    }
    const operation = entity.process?.actions.find(item => item.name === action);
    if (!operation) throw new ApiError(400, 'ERR_NOTIFICATION_ACTION', 'Choose an action from the record');
    if (operation.needsNote) return { text: `Thao tác “${operation.label}” cần ghi chú. Nhắn yêu cầu kèm tiêu đề hồ sơ và lý do cho trợ lý để tạo đề xuất.` };
    const proposal = await this.records.propose(owner, entry.target, { operation: 'action', action });
    return { proposal, text: proposal.webOnly ? `Cần xác nhận tác động trên web: ${new URL('/assistant', this.publicUrl)}` : proposal.summary };
  }
}
