// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Store } from '../dist/storage/store.js';
import { Notifications } from '../dist/notifications/service.js';
import { ApiError } from '../dist/errors.js';

const owner = { issuer: 'https://identity.invalid', subject: 'A' };
function setup(t) {
  const dir = mkdtempSync(join(tmpdir(), 'dxos-notification-test-'));
  const store = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  t.after(() => { store.close(); rmSync(dir, { recursive: true, force: true }); });
  store.setSecret(owner, 'telegram', { userId: '123' });
  store.setSecret(owner, 'core', { linked: true });
  let items = [], near = [], allowed = true, blocked;
  const context = { entity: { label: 'Request', fields: [], process: { states: [], actions: [] } }, record: { status: 'submitted' } };
  const notifications = new Notifications(store, { read: async (_owner, path) => {
    if (blocked) await blocked;
    return { items: path.startsWith('/inbox?') ? items : near, totalPages: 1 };
  } }, { record: async () => {
    if (!allowed) throw new ApiError(403, 'ERR_FORBIDDEN', 'Permission removed');
    return context;
  } }, new URL('https://example.invalid'));
  const add = () => { items = [{ model: 'demo', entity: 'request', id: 1, title: 'Demo', status: 'submitted', assignee: null, overdue: false }]; };
  return { notifications, store, context, add, due: () => { near = [{ id: 1 }]; },
    overdue: () => { items = items.map(item => ({ ...item, overdue: true })); }, revoke: () => { allowed = false; }, block: promise => { blocked = promise; } };
}
async function pending(t) {
  const fixture = setup(t);
  await fixture.notifications.configure(owner, true, 'UTC', 23);
  await fixture.notifications.tick(owner);
  fixture.add();
  const { deliveries } = await fixture.notifications.tick(owner);
  assert.equal(deliveries.length, 1);
  return { ...fixture, ref: deliveries[0].ref };
}
test('a lost delivery acknowledgement is not sent again after the next scan', async t => {
  const { notifications, ref } = await pending(t);
  await notifications.claim(owner, ref, true);
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
  await assert.rejects(notifications.claim(owner, ref, true), { errorCode: 'ERR_NOTIFICATION_CLOSED' });
});
test('removing record permission between scan and delivery prevents the send', async t => {
  const { notifications, ref, revoke } = await pending(t);
  revoke();
  await assert.rejects(notifications.claim(owner, ref, true), { status: 403 });
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
});
test('disabling notifications while a scan is in flight leaves no deliverable message', async t => {
  const { notifications, add, block } = setup(t);
  await notifications.configure(owner, true, 'UTC', 23);
  await notifications.tick(owner);
  add();
  let release;
  block(new Promise(resolve => { release = resolve; }));
  const scan = notifications.tick(owner);
  const disable = notifications.configure(owner, false, 'UTC', 23);
  release();
  const { deliveries } = await scan;
  await disable;
  assert.equal(deliveries.length, 1);
  await assert.rejects(notifications.claim(owner, deliveries[0].ref, true), { errorCode: 'ERR_NOTIFICATION_EXPIRED' });
});

test('notification actions respect the caller action list from Core metadata', async t => {
  const { notifications, context, ref } = await pending(t);
  Object.assign(context.entity, { canHandle: true, handlerActions: ['start'] });
  context.entity.process.actions = [
    { name: 'start', label: 'Start', from: ['submitted'], by: 'HANDLER' },
    { name: 'approve', label: 'Approve', from: ['submitted'], by: 'HANDLER' },
  ];
  const result = await notifications.action(owner, ref, 'view');
  assert.deepEqual(result.presentation.blocks[0].buttons.map(button => button.label), ['Start']);
});

test('Telegram rate limits retry at the specified time and stop after three attempts', async t => {
  t.mock.timers.enable({ apis: ['Date'], now: Date.UTC(2026, 10, 9, 10) });
  const { notifications, ref } = await pending(t);
  for (let attempt = 0; attempt < 3; attempt++) {
    await notifications.claim(owner, ref, true);
    await notifications.ack(owner, ref, undefined, 429, 2);
    assert.equal((await notifications.tick(owner)).deliveries.length, 0);
    t.mock.timers.tick(2001);
  }
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
  await assert.rejects(notifications.claim(owner, ref, true), { errorCode: 'ERR_NOTIFICATION_CLOSED' });
});

test('a restarted notification service does not resend an acknowledged delivery', async t => {
  const { notifications, store, ref } = await pending(t);
  await notifications.claim(owner, ref, true);
  await notifications.ack(owner, ref, 'synthetic-message');
  const restarted = new Notifications(store, { read: async () => ({ items: [], totalPages: 1 }) }, {}, new URL('https://example.invalid'));
  assert.equal((await restarted.tick(owner)).deliveries.length, 0);
  await assert.rejects(restarted.claim(owner, ref, true), { errorCode: 'ERR_NOTIFICATION_CLOSED' });
});

test('a record closed after scan is not delivered as pending work', async t => {
  const { notifications, context, ref } = await pending(t);
  context.entity.process.states = [{ name: 'done', terminal: true }];
  context.record.status = 'done';
  await assert.rejects(notifications.claim(owner, ref, true), { status: 404 });
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
});

test('daily digest follows the account timezone and does not repeat on the same day', async t => {
  t.mock.timers.enable({ apis: ['Date'], now: Date.UTC(2026, 10, 9, 0, 59) });
  const { notifications } = setup(t);
  await notifications.configure(owner, true, 'Asia/Ho_Chi_Minh', 8);
  await notifications.tick(owner);
  t.mock.timers.tick(60000);
  const result = await notifications.tick(owner);
  assert.equal(result.deliveries.length, 1);
  await notifications.claim(owner, result.deliveries[0].ref, true);
  await notifications.ack(owner, result.deliveries[0].ref, 'synthetic-digest');
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
  t.mock.timers.tick(86400000);
  assert.equal((await notifications.tick(owner)).deliveries.length, 1);
});

test('pagination is complete before the notification baseline advances', async t => {
  const { store } = setup(t);
  let secondPage = [], totalPages = 2;
  const core = { read: async (_owner, path) => path.startsWith('/inbox?')
    ? { items: new URL('https://core.invalid' + path).searchParams.get('page') === '1' ? secondPage : [], totalPages }
    : { items: [], totalPages: 1 } };
  const notifications = new Notifications(store, core, {}, new URL('https://example.invalid'));
  await notifications.configure(owner, true, 'UTC', 23);
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
  secondPage = [{ model: 'demo', entity: 'request', id: 1, status: 'submitted' }]; totalPages = 30;
  assert.equal((await notifications.tick(owner)).deliveries.length, 0);
  assert.equal(notifications.status(owner).error.includes('budget'), true);
  totalPages = 2;
  assert.equal((await notifications.tick(owner)).deliveries.length, 1);
  assert.equal(notifications.status(owner).error, null);
});

test('deadline notifications are emitted once for due-soon and overdue transitions', async t => {
  t.mock.timers.enable({ apis: ['Date'], now: Date.UTC(2026, 10, 9, 10) });
  const { notifications, ref, due, overdue } = await pending(t);
  await notifications.claim(owner, ref, true); await notifications.ack(owner, ref, 'synthetic-new');
  for (const [advance, label] of [[due, 'sắp đến hạn'], [overdue, 'đã quá hạn']]) {
    advance();
    const result = await notifications.tick(owner); assert.equal(result.deliveries.length, 1);
    const notice = await notifications.claim(owner, result.deliveries[0].ref, true);
    assert.equal(notice.text.includes(label), true);
    await notifications.ack(owner, result.deliveries[0].ref, 'synthetic-deadline');
    assert.equal((await notifications.tick(owner)).deliveries.length, 0);
  }
});

test('record cards use labels, bounded plain text and the configured timezone', async () => {
  const { recordDetails } = await import('../dist/notifications/format.js');
  const entity = { label: 'Hồ sơ', fields: [
    { name: 'title', label: 'Tiêu đề', type: 'text' }, { name: 'done', label: 'Hoàn tất', type: 'boolean' },
    { name: 'owner', label: 'Người tạo', type: 'user' }, { name: 'empty', label: 'Trống', type: 'text' },
  ], process: { states: [{ name: 'todo', label: 'Mới' }] } };
  const text = recordDetails(entity, { id: 'internal-identifier', status: 'todo', title: '#_*'.repeat(1000),
    done: true, owner: 'internal-identifier', empty: null, handle_by: '2026-11-09T01:00:00Z' }, 'Asia/Ho_Chi_Minh');
  assert.equal(text.includes('Trạng thái: Mới'), true); assert.equal(text.includes('Hoàn tất: Có'), true);
  assert.equal(text.includes('08:00'), true); assert.equal(text.includes('internal-identifier'), false);
  assert.equal(text.includes('Trống:'), false); assert.equal(text.includes('#_*'), true);
  assert.equal(text.includes('\\_'), false); assert.equal(text.length <= 3500, true);
});
