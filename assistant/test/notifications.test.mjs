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
  let items = [], allowed = true, blocked;
  const notifications = new Notifications(store, { read: async (_owner, path) => {
    if (blocked) await blocked;
    return { items: path.startsWith('/inbox?') ? items : [], totalPages: 1 };
  } }, { record: async () => {
    if (!allowed) throw new ApiError(403, 'ERR_FORBIDDEN', 'Permission removed');
    return { entity: { label: 'Request', fields: [], process: { states: [] } }, record: { status: 'submitted' } };
  } }, new URL('https://example.invalid'));
  const add = () => { items = [{ model: 'demo', entity: 'request', id: 1, title: 'Demo', status: 'submitted', assignee: null, overdue: false }]; };
  return { notifications, add, revoke: () => { allowed = false; }, block: promise => { blocked = promise; } };
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
