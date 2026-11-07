// SPDX-License-Identifier: Apache-2.0
import { randomBytes, randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const result = data => ({ content: [{ type: 'text', text: JSON.stringify(data) }], details: data });
async function bridge(path, input, confirmation = false) {
  const response = await fetch(new URL(path, process.env.DXOS_BRIDGE_URL), {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${confirmation
      ? process.env.DXOS_CONFIRMATION_TOKEN : process.env.DXOS_TOOL_TOKEN}` },
    body: JSON.stringify(input),
  });
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    const reason = /^[A-Z_]{1,80}$/.test(body.data?.reason ?? '') ? ` (${body.data.reason})` : '';
    const error = new Error(`DX-OS: ${body.message ?? 'Chưa thực hiện được yêu cầu'}${reason}`);
    error.dxosCode = /^ERR_[A-Z_]{1,80}$/.test(body.errorCode ?? '') ? body.errorCode : 'ERR_BRIDGE';
    throw error;
  }
  return body.data;
}

export default function register(api) {
  if (process.env.DXOS_PROBE_MARKER) {
    api.registerTool({ name: 'dxos_probe', description: 'Return the synthetic connection check result.',
      parameters: { type: 'object', properties: {}, additionalProperties: false },
      execute: async () => { writeFileSync(process.env.DXOS_PROBE_MARKER, 'ok', { mode: 0o600 }); return result({ ok: true }); },
    });
    return;
  }
  const pending = new Map();
  registerNotifications(api);
  const field = { type: 'string' };
  const target = { model: field, entity: field, id: { type: 'integer', minimum: 1 } };
  const page = { page: { type: 'integer', minimum: 0, description: 'Zero-based page index. Use 0 for the first page.' }, size: { type: 'integer', minimum: 1, maximum: 100 } };
  const filters = { type: 'array', maxItems: 6, items: { type: 'object', required: ['field', 'op', 'value'],
    additionalProperties: false, properties: { field, op: { type: 'string', enum: ['eq', 'ne', 'lt', 'lte', 'gt', 'gte', 'like', 'in'] }, value: field } } };
  const tools = [
    ['models', 'Đọc mô hình và quyền được phép, dùng metadata để chọn trường và hành động.', {}, []],
    ['inbox', 'Đọc hộp việc. Trang đầu là page=0, không phải 1. Nếu trang rỗng nhưng totalElements>0, đọc lại page=0; không kết luận không có việc.', page, []],
    ['record', 'Đọc chi tiết hồ sơ và metadata hiện tại.', target, ['model', 'entity', 'id']],
    ['events', 'Đọc lịch sử hồ sơ, tối đa 100 sự kiện gần nhất.', target, ['model', 'entity', 'id']],
    ['handlers', 'Đọc người có thể nhận việc, chọn đúng người; tên trùng phải hỏi lại.', target, ['model', 'entity', 'id']],
    ['list', 'Lọc hồ sơ theo trường metadata, không phải tìm toàn văn.', { model: field, entity: field, ...page, filters }, ['model', 'entity']],
    ['stats', 'Đọc số liệu và phạm vi do Core tính, giữ computedAt.', { model: field, entity: field, filters }, ['model', 'entity']],
  ];
  for (const [name, description, properties, required] of tools) api.registerTool({
    name: `dxos_${name}`, description,
    parameters: { type: 'object', properties, required, additionalProperties: false },
    execute: async (_id, params) => result(await bridge('/internal/v1/tools', { ...params, tool: name })),
  });
  api.registerTool({ name: 'dxos_admin', description: 'Quản trị nền tảng, chỉ ADMIN: models=danh sách/phiên bản/tạm giữ; runs=lượt triển khai; outbox=hàng đợi tác vụ tích hợp và số lượng; audit=nhật ký kiểm toán; plan=xem trước YAML. Không áp dụng mô hình hoặc sửa quyền.',
    parameters: { type: 'object', additionalProperties: false, required: ['adminOperation'], properties: {
      adminOperation: { type: 'string', enum: ['models', 'runs', 'outbox', 'audit', 'plan'] },
      model: field, yaml: { type: 'string', maxLength: 20000 },
    } }, execute: async (_id, params) => result(await bridge('/internal/v1/tools', { ...params, tool: 'admin' })),
  });
  api.on('before_tool_call', async event => {
    if (event.toolName !== 'dxos_propose') return;
    const { nonce: _nonce, ...params } = event.params;
    let proposal;
    try { proposal = await bridge('/internal/v1/tools', { ...params, tool: 'propose' }); }
    catch (error) {
      const code = /^ERR_[A-Z_]{1,80}$/.test(error.dxosCode ?? '') ? error.dxosCode : 'ERR_BRIDGE';
      return { block: true, blockReason: `Chưa tạo được đề xuất (${code}), chưa thực hiện thao tác. Đối chiếu hồ sơ và mã hành động từ metadata; không đoán nguyên nhân thiếu quyền hoặc tự gửi lại lệnh ghi.` };
    }
    for (const [key, item] of pending) if (item.expires < Date.now()) pending.delete(key);
    const nonce = randomBytes(24).toString('base64url');
    pending.set(nonce, { proposal, approved: false, expires: proposal.expires });
    if (proposal.webOnly) return { params: { ...params, nonce } };
    return { params: { ...params, nonce }, requireApproval: {
      title: 'Xác nhận thao tác DX-OS', description: proposal.summary,
      severity: 'warning', allowedDecisions: ['allow-once', 'deny'], timeoutMs: 120000,
      async onResolution(decision) {
        const item = pending.get(nonce);
        if (!item) return;
        item.approved = decision === 'allow-once';
        if (!item.approved) { pending.delete(nonce); await cancelProposal(item.proposal.proposalId); }
      },
    } };
  });
  api.registerTool({ name: 'dxos_propose', description: 'Trước khi đề xuất: đọc models, list rồi record. Dùng đúng target.id và action.name từ kết quả, không đoán từ tiêu đề hoặc nhãn. Nền tảng yêu cầu xác nhận thật trước khi ghi.',
    parameters: { type: 'object', additionalProperties: false, required: ['model', 'entity', 'id', 'operation'],
      properties: { ...target, operation: { type: 'string', enum: ['action', 'assign'] }, action: { type: 'string', description: 'Exact process.actions[].name from dxos_record metadata, never the translated label.' },
        assignee: { anyOf: [field, { type: 'null' }] }, note: { type: 'string', maxLength: 2000 },
        nonce: { type: 'string', description: 'Internal reference supplied by the host' } } },
    execute: async (_id, params) => {
      const item = pending.get(params.nonce); pending.delete(params.nonce);
      if (!item || item.expires < Date.now()) throw new Error('Đề xuất đã hết hạn, cần tạo lại.');
      if (item.proposal.webOnly) return result({ proposalId: item.proposal.proposalId,
        summary: item.proposal.summary, execution: { state: 'pending_confirmation', coreCommitted: false, confirmationChannel: 'web' }, message: 'Chưa thực hiện. Cần xác nhận tác động trên trang Trợ lý của DX-OS.' });
      if (!item.approved) throw new Error('Chưa có xác nhận hợp lệ, chưa thực hiện thao tác.');
      const userId = process.env.DXOS_TELEGRAM_USER;
      const record = await bridge('/internal/v1/confirm', { proposalId: item.proposal.proposalId,
        userId, chatId: userId }, true);
      return result({ execution: { state: 'succeeded', coreCommitted: true, confirmed: true },
        record, ...(params.operation === 'assign' ? { recipientDelivery: { state: 'not_verified' } } : {}),
        message: params.operation === 'assign'
          ? 'Core đã ghi nhận giao việc sau xác nhận. Không yêu cầu xác nhận lại. Chưa xác minh thông báo hoặc việc người nhận đã đọc/nhận xử lý.'
          : 'Core đã ghi nhận chuyển trạng thái sau xác nhận. Không yêu cầu xác nhận lại. Đối chiếu trạng thái trong record.' });
    },
  }, { optional: true });
}

async function cancelProposal(proposalId) {
  const userId = process.env.DXOS_TELEGRAM_USER;
  await bridge('/internal/v1/cancel', { proposalId, userId, chatId: userId }, true);
}

function registerNotifications(api) {
  if (!api.registerService || !api.registerInteractiveHandler) return;
  let job, signal, running = false, stopped = true;
  async function cycle() {
    if (running || stopped) return;
    running = true;
    try {
      const { deliveries } = await bridge('/internal/v1/notifications', { operation: 'tick' });
      for (const { ref } of deliveries) {
        if (stopped) break;
        let authorized = false;
        try {
          const card = await bridge('/internal/v1/notifications', { operation: 'claim', ref });
          const adapter = await api.runtime.channel.outbound.loadAdapter('telegram');
          if (!adapter?.sendPayload) throw new Error('Telegram unavailable');
          await bridge('/internal/v1/notifications', { operation: 'authorize', ref }); authorized = true;
          if (stopped) break;
          const receipt = await adapter.sendPayload({ cfg: api.config, to: card.to, text: card.text, signal,
            payload: { text: card.text, presentation: card.presentation } });
          await bridge('/internal/v1/notifications', { operation: 'ack', ref,
            messageId: String(receipt.messageId ?? receipt.messageIds?.[0] ?? '') || undefined });
        } catch (error) {
          if (authorized) {
            const status = Number(error.error_code ?? error.statusCode ?? error.status);
            const retryAfter = Number(error.parameters?.retry_after ?? error.retryAfter);
            await bridge('/internal/v1/notifications', { operation: 'ack', ref,
              ...(Number.isFinite(status) ? { status } : {}),
              ...(Number.isFinite(retryAfter) ? { retryAfter } : {}) }).catch(() => {});
          }
        }
      }
    } catch { /* Sync status is exposed on the account page without provider errors. */ }
    finally { running = false; }
  }
  api.registerService({ id: 'dxos-notifications', apiVersion: 2,
    start({ scheduler }) { stopped = false; signal = scheduler.signal;
      job = scheduler.schedule({ id: 'scan-inbox', delayMs: 0, everyMs: 60000, run: cycle }); },
    stop() { stopped = true; job?.cancel(); } });
  const waiting = new Set();
  api.registerInteractiveHandler({ channel: 'telegram', namespace: 'dxos_notice', async handler(ctx) {
    const reply = (text, presentation) => ctx.respond.reply({ text, ...(presentation ? { presentation } : {}) });
    if (!ctx.auth?.isAuthorizedSender || ctx.isGroup) { await reply('Bạn không có quyền dùng nút này.'); return { handled: true }; }
    const [ref, action, extra] = String(ctx.callback?.payload ?? '').split(':');
    if (extra || !/^[\w-]{24}$/.test(ref) || !/^[a-z_]{1,20}$/.test(action)) return { handled: true };
    const key = ref + ':' + action;
    if (waiting.has(key)) { await reply('Đang chờ xử lý yêu cầu này.'); return { handled: true }; }
    waiting.add(key);
    try {
      const userId = String(ctx.senderId), chatId = String(ctx.callback.chatId);
      const item = await bridge('/internal/v1/notifications', { operation: 'action', ref, action, userId, chatId }, true);
      if (!item.proposal || item.proposal.webOnly) await reply(item.text, item.presentation);
      else {
        const request = await api.runtime.gateway.request('plugin.approval.request', {
          pluginId: 'dxos-assistant', toolName: 'dxos_propose', toolCallId: randomUUID(),
          agentId: 'main', sessionKey: 'agent:main:main', title: 'Xác nhận thao tác DX-OS',
          description: item.proposal.summary, severity: 'warning', allowedDecisions: ['allow-once', 'deny'],
          timeoutMs: 120000, twoPhase: true, turnSourceChannel: 'telegram', turnSourceTo: chatId,
          turnSourceAccountId: ctx.accountId,
        });
        if (!request?.id) throw new Error('Approval unavailable');
        const resolution = Object.hasOwn(request, 'decision') ? request :
          await api.runtime.gateway.request('plugin.approval.waitDecision', { id: request.id }, { timeoutMs: 125000 });
        if (resolution?.id !== request.id || resolution.decision !== 'allow-once') {
          await cancelProposal(item.proposal.proposalId);
          await reply('Đã hủy hoặc hết hạn; chưa thực hiện thao tác.');
        }
        else {
          await bridge('/internal/v1/confirm', { proposalId: item.proposal.proposalId, userId, chatId }, true);
          await reply('Thao tác đã được Core ghi nhận. Bạn có thể đối chiếu trạng thái và lịch sử trên DX-OS.');
        }
      }
    } catch { await reply('Chưa hoàn tất thao tác. Kiểm tra trạng thái và lịch sử trên DX-OS trước khi thử lại.'); }
    finally { waiting.delete(key); }
    return { handled: true };
  } });
}
