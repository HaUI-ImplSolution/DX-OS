// SPDX-License-Identifier: Apache-2.0
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { ApiError } from '../errors.js';
import type { Profiles, Profile } from '../providers/profiles.js';
import type { Runtimes } from '../runtime/slots.js';
import type { TelegramAccounts } from './telegram.js';
import type { Records } from '../tools/records.js';
import type { Store } from '../storage/store.js';
import type { Identity } from '../auth/identity.js';
import type { Notifications } from '../notifications/service.js';

export type AccountsServices = { profiles: Profiles; runtimes: Runtimes; telegram: TelegramAccounts;
  records: Records; store: Store; notifications: Notifications; publicUrl: URL;
  verify: (token: string | undefined) => Promise<Identity> };
const ok = (data: unknown) => ({ success: true, message: 'OK', data });
const idParams = { type: 'object', required: ['id'], properties: { id: {
  type: 'string', pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
} } };
const emptyBody = { type: 'object', additionalProperties: false, properties: {} };

export function accountRoutes(app: FastifyInstance, services: AccountsServices) {
  const owner = (request: FastifyRequest) => services.verify(request.headers.authorization);
  app.addHook('preHandler', async request => {
    if (request.url.startsWith('/assistant-api/v1/me/') && !['GET', 'HEAD'].includes(request.method) &&
        request.headers.origin !== services.publicUrl.origin)
      throw new ApiError(403, 'ERR_ORIGIN', 'The request origin is not allowed');
  });
  app.get('/assistant-api/v1/me/providers', async request => ok(services.profiles.list(await owner(request))));
  const profileBody = { type: 'object', additionalProperties: false,
    required: ['provider', 'model', 'apiKey'], properties: {
      provider: { type: 'string', enum: ['gemini', 'openai', 'anthropic', 'compatible'] },
      model: { type: 'string', pattern: '^[A-Za-z0-9_.:/-]{1,160}$' },
      apiKey: { type: 'string', minLength: 1, maxLength: 4096, writeOnly: true },
      baseUrl: { type: 'string', minLength: 1, maxLength: 2048 },
    } };
  app.post<{ Body: Omit<Profile, 'id'> }>('/assistant-api/v1/me/providers', {
    schema: { body: profileBody },
  }, async request => ok({ id: await services.profiles.save(await owner(request), request.body) }));
  app.patch<{ Params: { id: string }; Body: Omit<Profile, 'id'> }>('/assistant-api/v1/me/providers/:id', {
    schema: { params: idParams, body: profileBody },
  }, async request => ok({ id: await services.profiles.save(await owner(request), { ...request.body, id: request.params.id }) }));
  app.delete<{ Params: { id: string } }>('/assistant-api/v1/me/providers/:id', { schema: { params: idParams } },
    async request => { await services.profiles.remove(await owner(request), request.params.id); return ok(null); });
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/providers/:id/select', {
    schema: { params: idParams, body: emptyBody },
  }, async request => { await services.profiles.select(await owner(request), request.params.id); return ok(null); });
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/providers/:id/probe', {
    schema: { params: idParams, body: { type: 'object', additionalProperties: false,
      required: ['acknowledgeCost'], properties: { acknowledgeCost: { const: true } } } },
  }, async request => {
    const identity = await owner(request);
    return ok(await services.runtimes.probe(identity, services.profiles.get(identity, request.params.id)));
  });
  app.get('/assistant-api/v1/me/telegram', async request => ok(services.telegram.status(await owner(request))));
  app.put<{ Body: { token: string } }>('/assistant-api/v1/me/telegram', {
    schema: { body: { type: 'object', required: ['token'], additionalProperties: false,
      properties: { token: { type: 'string', minLength: 1, maxLength: 256, writeOnly: true } } } },
  }, async request => { await services.telegram.configure(await owner(request), request.body.token); return ok(null); });
  app.delete('/assistant-api/v1/me/telegram', { schema: { body: emptyBody } },
    async request => { await services.telegram.unlink(await owner(request)); return ok(null); });
  app.post<{ Body: { code: string } }>('/assistant-api/v1/me/telegram/pairing', {
    schema: { body: { type: 'object', required: ['code'], additionalProperties: false,
      properties: { code: { type: 'string', pattern: '^[A-Z0-9]{6,12}$' } } } },
  }, async request => { await services.telegram.pair(await owner(request), request.body.code); return ok(null); });
  app.get('/assistant-api/v1/me/runtime', async request => ok(await services.runtimes.status(await owner(request))));
  app.get('/assistant-api/v1/me/notifications', async request => ok(services.notifications.status(await owner(request))));
  app.put<{ Body: { enabled: boolean; timeZone: string; digestHour: number } }>('/assistant-api/v1/me/notifications', {
    schema: { body: { type: 'object', required: ['enabled', 'timeZone', 'digestHour'], additionalProperties: false,
      properties: { enabled: { type: 'boolean' }, timeZone: { type: 'string', minLength: 1, maxLength: 100 },
        digestHour: { type: 'integer', minimum: 0, maximum: 23 } } } },
  }, async request => ok(await services.notifications.configure(await owner(request), request.body.enabled,
    request.body.timeZone, request.body.digestHour)));
  app.post('/assistant-api/v1/me/runtime/start', { schema: { body: emptyBody } },
    async request => ok(await services.runtimes.start(await owner(request))));
  app.post('/assistant-api/v1/me/runtime/stop', { schema: { body: emptyBody } },
    async request => ok(await services.runtimes.stop(await owner(request))));
  app.get<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id', {
    schema: { params: idParams },
  }, async request => ok(await services.records.proposal(await owner(request), request.params.id)));
  app.get<{ Querystring: { page?: number; size?: number } }>('/assistant-api/v1/me/proposals', {
    schema: { querystring: { type: 'object', additionalProperties: false, properties: {
      page: { type: 'integer', minimum: 0, maximum: 10000 }, size: { type: 'integer', minimum: 1, maximum: 5 },
    } } },
  }, async request => ok(await services.records.proposals(await owner(request), request.query.page ?? 0, request.query.size ?? 5)));
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id/confirm', {
    schema: { params: idParams, body: emptyBody },
  }, async request => ok(await services.records.execute(await owner(request), request.params.id, 'web')));
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id/cancel', {
    schema: { params: idParams, body: emptyBody },
  }, async request => { services.store.cancel(await owner(request), request.params.id); return ok(null); });
  app.get('/assistant-api/v1/admin/capacity', async request => {
    if (!(await owner(request)).admin) throw new ApiError(403, 'ERR_FORBIDDEN', 'Administrator role required');
    return ok(services.runtimes.capacity());
  });
}
