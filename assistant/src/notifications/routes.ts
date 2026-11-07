// SPDX-License-Identifier: Apache-2.0
import type { FastifyInstance } from 'fastify';
import type { Runtimes } from '../runtime/slots.js';
import type { Notifications } from './service.js';
import { ApiError } from '../errors.js';
import type { Store } from '../storage/store.js';
import type { Telegram } from '../accounts/telegram.js';

export function notificationRoutes(app: FastifyInstance, runtimes: Runtimes, notifications: Notifications, store: Store) {
  app.post<{ Body: { operation: string; ref?: string; messageId?: string; status?: number; retryAfter?: number;
    action?: string; userId?: string; chatId?: string } }>('/internal/v1/notifications', {
    schema: { body: { type: 'object', additionalProperties: false, required: ['operation'], properties: {
      operation: { type: 'string', enum: ['tick', 'claim', 'authorize', 'ack', 'action'] },
      ref: { type: 'string', pattern: '^[A-Za-z0-9_-]{24}$' },
      messageId: { type: 'string', minLength: 1, maxLength: 100 }, status: { type: 'integer' },
      retryAfter: { type: 'number', minimum: 0, maximum: 86400 }, action: { type: 'string', pattern: '^[a-z_]{1,20}$' },
      userId: { type: 'string', pattern: '^[0-9]+$' }, chatId: { type: 'string', pattern: '^[0-9]+$' },
    } } },
  }, async request => {
    const input = request.body;
    const owner = runtimes.authorize(request.headers.authorization?.replace(/^Bearer /, '') ?? '',
      input.operation === 'action' ? 'confirmation' : 'tool');
    let data: unknown;
    if (input.operation === 'tick') data = await notifications.tick(owner);
    else {
      if (!input.ref) throw new ApiError(400, 'ERR_NOTIFICATION_REF', 'Choose a notification');
      if (input.operation === 'claim' || input.operation === 'authorize')
        data = await notifications.claim(owner, input.ref, input.operation === 'authorize');
      else if (input.operation === 'ack') {
        await notifications.ack(owner, input.ref, input.messageId, input.status, input.retryAfter); data = null;
      } else {
        const paired = store.getSecret<Telegram>(owner, 'telegram');
        if (!paired?.userId || paired.userId !== input.userId || paired.userId !== input.chatId)
          throw new ApiError(403, 'ERR_TELEGRAM_IDENTITY', 'The action belongs to another chat');
        data = await notifications.action(owner, input.ref, input.action ?? 'view');
      }
    }
    return { success: true, message: 'OK', data };
  });
}
