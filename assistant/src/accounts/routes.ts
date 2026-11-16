// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { ApiError } from '../errors.js';
import type { Records } from '../tools/records.js';
import type { Store } from '../storage/store.js';
import type { Identity } from '../auth/identity.js';
const ok = (data: unknown) => ({ success: true, message: 'OK', data });
const idParams = { type: 'object', required: ['id'], properties: { id: {
  type: 'string', pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
} } };
const emptyBody = { type: 'object', additionalProperties: false, properties: {} };

export function fieldRoutes(app: FastifyInstance, services: {
  fields: import('../tools/fields.js').FieldProposals; records: Records; store: Store;
  publicUrl: URL; verify: (token: string | undefined) => Promise<Identity>;
}) {
  const owner = (request: FastifyRequest) => services.verify(request.headers.authorization);
  app.addHook('preHandler', async request => {
    if (request.url.startsWith('/assistant-api/v1/me/') && !['GET','HEAD'].includes(request.method) &&
        request.headers.origin !== services.publicUrl.origin)
      throw new ApiError(403, 'ERR_ORIGIN', 'The request origin is not allowed');
  });
  const createBody = { type: 'object', additionalProperties: false, required: ['model','entity','id','task'], properties: {
    model: { type: 'string', pattern: '^[a-z][a-z0-9_]{0,39}$' },
    entity: { type: 'string', pattern: '^[a-z][a-z0-9_]{0,39}$' },
    id: { type: 'integer', minimum: 1, maximum: Number.MAX_SAFE_INTEGER },
    task: { type: 'string', pattern: '^[a-z][a-z0-9_]{0,39}$' },
  } };
  app.post<{ Body: import('../tools/core-client.js').Target & { task: string } }>('/assistant-api/v1/me/proposals', {
    schema: { body: createBody, description: "Create one field proposal with cited sources and bounded, read-only warnings about declared input fields; confirmation writes only the proposed output field." },
  }, async (request, reply) => {
    const identity = await owner(request); const controller = new AbortController();
    const close = () => { if (!reply.raw.writableFinished) controller.abort(); };
    reply.raw.on('close', close);
    try { return ok(await services.fields.create(identity, request.body, request.body.task, controller.signal)); }
    finally { reply.raw.off('close', close); }
  });
  app.get<{ Querystring: { page?: number; size?: number } }>('/assistant-api/v1/me/proposals', {
    schema: { querystring: { type: 'object', additionalProperties: false, properties: {
      page: { type: 'integer', minimum: 0, maximum: 10000 }, size: { type: 'integer', minimum: 1, maximum: 5 },
    } } },
  }, async request => ok(await services.records.proposals(await owner(request), request.query.page ?? 0, request.query.size ?? 5)));
  app.get<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id', { schema: { params: idParams } },
    async request => ok(await services.records.proposal(await owner(request), request.params.id)));
  app.post<{ Params: { id: string }; Body: { value: string } }>('/assistant-api/v1/me/proposals/:id/revise', {
    schema: { params: idParams, body: { type: 'object', additionalProperties: false, required: ['value'],
      properties: { value: { type: 'string', minLength: 1, maxLength: 2000 } } } },
  }, async request => ok(await services.fields.revise(await owner(request), request.params.id, request.body.value)));
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id/confirm', {
    schema: { params: idParams, body: emptyBody, description: 'Confirm a changed field; 409 ERR_PROPOSAL_NO_CHANGE for an identical value. Success reports changed=true after Core advances the record version.' },
  }, async request => ok(await services.fields.execute(await owner(request), request.params.id)));
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id/cancel', {
    schema: { params: idParams, body: emptyBody },
  }, async request => {
    const identity = await owner(request);
    await services.records.proposal(identity, request.params.id);
    services.store.cancel(identity, request.params.id); return ok(null);
  });
  for (const path of ['providers','telegram','runtime','notifications']) {
    for (const route of [`/assistant-api/v1/me/${path}`, `/assistant-api/v1/me/${path}/*`]) {
      app.route({ method: ['GET','POST','PUT','PATCH','DELETE'], url: route, handler: async request => {
        await owner(request);
        throw new ApiError(410, 'ERR_FEATURE_RETIRED', 'This assistant feature has been retired');
      } });
    }
  }
}
