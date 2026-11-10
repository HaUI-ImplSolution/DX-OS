// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import register from '../runtime/plugin/index.mjs';

function setup(t, webOnly = false) {
  const tools = new Map(); let hook; let executions = 0;
  const previous = globalThis.fetch;
  const previousUrl = process.env.DXOS_BRIDGE_URL;
  process.env.DXOS_BRIDGE_URL = 'http://synthetic.invalid';
  globalThis.fetch = async (url, options) => {
    const data = url.pathname === '/internal/v1/confirm'
      ? (++executions, { status: 'working' })
      : { proposalId: 'synthetic-proposal', summary: 'Demo', expires: Date.now() + 120000, webOnly };
    return new Response(JSON.stringify({ success: true, data }));
  };
  t.after(() => {
    globalThis.fetch = previous;
    if (previousUrl === undefined) delete process.env.DXOS_BRIDGE_URL;
    else process.env.DXOS_BRIDGE_URL = previousUrl;
  });
  register({ registerTool: tool => tools.set(tool.name, tool), on: (_name, callback) => { hook = callback; } });
  return { tool: tools.get('dxos_propose'), hook: event => hook(event), executions: () => executions };
}
const event = { toolName: 'dxos_propose', params: { model: 'demo', entity: 'request', id: 1, operation: 'action', action: 'start' } };
test('a forged or unapproved host reference cannot execute', async t => {
  const { tool, hook, executions } = setup(t);
  await assert.rejects(tool.execute('call', { ...event.params, nonce: 'forged' }));
  const pending = await hook(event);
  await assert.rejects(tool.execute('call', pending.params));
  assert.equal(executions(), 0);
});
test('native approval executes only the server snapshot, once', async t => {
  const { tool, hook, executions } = setup(t);
  const pending = await hook(event);
  await pending.requireApproval.onResolution('allow-once');
  const receipt = await tool.execute('call', pending.params);
  assert.equal(receipt.details.execution.coreCommitted, true);
  assert.equal(receipt.details.execution.confirmed, true);
  assert.equal(receipt.details.recipientDelivery, undefined);
  await assert.rejects(tool.execute('again', pending.params));
  assert.equal(executions(), 1);
});
test('web-only decisions do not execute from the model tool', async t => {
  const { tool, hook, executions } = setup(t, true);
  const pending = await hook(event);
  assert.equal(pending.requireApproval, undefined);
  const result = await tool.execute('call', pending.params);
  assert.equal(result.details.proposalId, 'synthetic-proposal');
  assert.equal(executions(), 0);
});

test('a capability check stops the child after the tool ran even if the CLI stays alive', async t => {
  const { spawn } = await import('node:child_process');
  const { mkdtempSync, rmSync } = await import('node:fs');
  const { tmpdir } = await import('node:os');
  const { join } = await import('node:path');
  const { waitForCapability } = await import('../dist/runtime/probe.js');
  const dir = mkdtempSync(join(tmpdir(), 'dxos-probe-test-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const marker = join(dir, 'called');
  const child = spawn(process.execPath, ['-e', 'require("fs").writeFileSync(process.argv[1],"ok"); setInterval(()=>{},1000)', marker], { detached: true, stdio: 'ignore' });
  assert.equal(await waitForCapability(child, marker, 5000), true);
  assert.notEqual(child.signalCode, null);
  const missing = spawn(process.execPath, ['-e', 'setInterval(()=>{},1000)'], { detached: true, stdio: 'ignore' });
  assert.equal(await waitForCapability(missing, join(dir, 'missing'), 150), false);
  assert.notEqual(missing.signalCode, null);
});

test('a refused proposal is a blocked result without creating a native approval or leaking its error', async t => {
  const { hook, executions } = setup(t);
  globalThis.fetch = async () => new Response(JSON.stringify({ success: false, errorCode: 'ERR_INVALID_REQUEST', message: 'private diagnostic' }), { status: 400 });
  const decision = await hook(event);
  assert.equal(decision.block, true);
  assert.equal(decision.requireApproval, undefined);
  assert.match(decision.blockReason, /ERR_INVALID_REQUEST/);
  assert.doesNotMatch(decision.blockReason, /private diagnostic/);
  assert.equal(executions(), 0);
});

test('only an assignment receipt distinguishes unverified recipient delivery', async t => {
  const { tool, hook } = setup(t);
  const pending = await hook({ ...event, params: { ...event.params, operation: 'assign', action: undefined, assignee: 'synthetic-recipient' } });
  await pending.requireApproval.onResolution('allow-once');
  const receipt = await tool.execute('call', pending.params);
  assert.equal(receipt.details.execution.coreCommitted, true);
  assert.equal(receipt.details.recipientDelivery.state, 'not_verified');
});

test('denying native approval cancels its server proposal without executing', async t => {
  const { tool, hook, executions } = setup(t);
  const previousFetch = globalThis.fetch; const cancelled = [];
  globalThis.fetch = async (url, options) => {
    if (url.pathname === '/internal/v1/cancel') cancelled.push(JSON.parse(options.body).proposalId);
    return previousFetch(url, options);
  };
  const pending = await hook(event);
  await pending.requireApproval.onResolution('deny');
  assert.deepEqual(cancelled, ['synthetic-proposal']);
  await assert.rejects(tool.execute('call', pending.params));
  assert.equal(executions(), 0);
});

test('Telegram delivery errors reach the ledger without forwarding adapter details', async t => {
  const previous = globalThis.fetch, previousUrl = process.env.DXOS_BRIDGE_URL;
  const acknowledgements = []; let service, attempts = 0;
  process.env.DXOS_BRIDGE_URL = 'https://bridge.invalid';
  globalThis.fetch = async (_url, request) => {
    const body = JSON.parse(request.body);
    if (body.operation === 'ack') acknowledgements.push(body);
    const data = body.operation === 'tick' ? { deliveries: [{ ref: 'synthetic' }] } :
      body.operation === 'claim' ? { to: '100', text: 'Notice' } : {};
    return new Response(JSON.stringify({ success: true, data }));
  };
  t.after(() => {
    globalThis.fetch = previous;
    if (previousUrl === undefined) delete process.env.DXOS_BRIDGE_URL; else process.env.DXOS_BRIDGE_URL = previousUrl;
  });
  register({ config: {}, registerTool() {}, on() {}, registerInteractiveHandler() {},
    registerService(value) { service = value; }, runtime: { channel: { outbound: {
      loadAdapter: async () => ({ sendPayload: async () => {
        attempts++;
        if (attempts === 1) throw Object.assign(new Error('synthetic-sensitive-detail'), { error_code: 429, parameters: { retry_after: 2 } });
        throw new Error('synthetic-sensitive-detail');
      } }),
    } } } });
  let cycle;
  service.start({ scheduler: { signal: new AbortController().signal, schedule(job) { cycle = job.run; return { cancel() {} }; } } });
  await cycle(); await cycle(); service.stop();
  assert.deepEqual(acknowledgements, [
    { operation: 'ack', ref: 'synthetic', status: 429, retryAfter: 2 },
    { operation: 'ack', ref: 'synthetic' },
  ]);
});

test('permission refusal immediately before delivery prevents the Telegram send', async t => {
  const previous = globalThis.fetch, previousUrl = process.env.DXOS_BRIDGE_URL;
  let service, sends = 0;
  process.env.DXOS_BRIDGE_URL = 'https://bridge.invalid';
  globalThis.fetch = async (_url, request) => {
    const body = JSON.parse(request.body);
    if (body.operation === 'authorize') return new Response(JSON.stringify({ success: false, errorCode: 'ERR_FORBIDDEN', message: 'Permission removed' }), { status: 403 });
    return new Response(JSON.stringify({ success: true, data: body.operation === 'tick' ? { deliveries: [{ ref: 'synthetic' }] } : { to: '100', text: 'Notice' } }));
  };
  t.after(() => {
    globalThis.fetch = previous;
    if (previousUrl === undefined) delete process.env.DXOS_BRIDGE_URL; else process.env.DXOS_BRIDGE_URL = previousUrl;
  });
  register({ config: {}, registerTool() {}, on() {}, registerInteractiveHandler() {},
    registerService(value) { service = value; }, runtime: { channel: { outbound: {
      loadAdapter: async () => ({ sendPayload: async () => { sends++; } }),
    } } } });
  let cycle;
  service.start({ scheduler: { signal: new AbortController().signal, schedule(job) { cycle = job.run; return { cancel() {} }; } } });
  await cycle(); service.stop(); assert.equal(sends, 0);
});
