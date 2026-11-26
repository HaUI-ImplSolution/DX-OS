// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { ApiError } from '../errors.js';
import type { Records } from '../tools/records.js';
import type { Store } from '../storage/store.js';
import type { Identity } from '../auth/identity.js';
import type { Target } from '../tools/core-client.js';
const ok = (data: unknown) => ({ success: true, message: 'OK', data });
const idParams = { type: 'object', required: ['id'], properties: { id: {
  type: 'string', pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
} } };
const emptyBody = { type: 'object', additionalProperties: false, properties: {} };
const taskName = { type: 'string', pattern: '^[a-z][a-z0-9_]{0,39}$' };
const confidence = { type: 'number', minimum: 0, maximum: 1, description: 'Probability of the suggested classifier label.' };
const targetProperties = {
  model: taskName,
  entity: taskName,
  id: { type: 'integer', minimum: 1, maximum: Number.MAX_SAFE_INTEGER },
};

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
  const createBody = { type: 'object', additionalProperties: false, required: ['model','entity','id','task'],
    properties: { ...targetProperties, task: taskName } };
  app.post<{ Body: { items: Target[] } }>('/assistant-api/v1/me/reviews', {
    schema: {
      description: 'Review up to 25 records using the caller’s Core session. A session or link error fails the entire batch. Each returned item identifies a record and an eligible local classifier task. Individual record failures are counted in skipped. Read-only or closed records and unavailable or incompatible tasks are omitted. Items with a suggested label include confidence. Abstentions have suggested=null and mismatch=false. This operation creates no proposals and writes no records.',
      body: { type: 'object', additionalProperties: false, required: ['items'], properties: {
        items: { type: 'array', minItems: 1, maxItems: 25, items: { type: 'object', additionalProperties: false,
          required: ['model', 'entity', 'id'], properties: targetProperties } },
      } },
      response: { 200: { type: 'object', required: ['success', 'message', 'data'], properties: {
        success: { type: 'boolean' }, message: { type: 'string' },
        data: { type: 'object', required: ['items', 'skipped'], properties: {
          skipped: { type: 'integer', minimum: 0 }, items: { type: 'array', items: {
          type: 'object', required: ['model', 'entity', 'id', 'task', 'field', 'current', 'suggested', 'evidence', 'mismatch'],
          properties: { ...targetProperties, task: taskName, field: { type: 'string' }, current: { type: 'string', nullable: true },
            suggested: { type: 'string', nullable: true }, confidence, evidence: { type: 'array', items: { type: 'string' }, maxItems: 3 },
            mismatch: { type: 'boolean' } },
        } } } },
      } } },
    },
  }, async (request, reply) => {
    reply.header('Cache-Control', 'no-store');
    return ok(await services.fields.review(await owner(request), request.body.items));
  });
  app.post<{ Body: Target & { task: string } }>('/assistant-api/v1/me/proposals', {
    schema: { body: createBody, description: [
      'Create one field proposal using the task engine in entity metadata. Confirmation writes only the proposed output field.',
      'LLM proposals cite supplied sources and can include read-only warnings about declared input fields.',
      'Local classifier proposals have an empty snapshot.sources array, snapshot.evidence with one to three excerpts of the declared record fields, and snapshot.confidence with the probability of the suggested label.',
      'A classifier can abstain, returning insufficient=true and proposal=null.',
      '409 ERR_CLASSIFIER_UNAVAILABLE means the task weights could not be loaded.',
      '409 ERR_CLASSIFIER_LABELS means the output options differ from the labels of the classifier.',
      '409 ERR_INFERENCE_REQUIRED means an LLM task has no inference endpoint.',
    ].join(' '), response: { 200: {
      type: 'object', required: ['success', 'message', 'data'], properties: {
        success: { type: 'boolean' }, message: { type: 'string' },
        data: { type: 'object', required: ['insufficient', 'proposal'], properties: {
          insufficient: { type: 'boolean' },
          proposal: { type: 'object', nullable: true, additionalProperties: true, required: ['snapshot'], properties: {
            snapshot: { type: 'object', additionalProperties: true, properties: { confidence } },
          } },
        } },
      },
    }, 409: {
      description: 'Task configuration or record conflict, including ERR_CLASSIFIER_UNAVAILABLE and ERR_CLASSIFIER_LABELS.',
      type: 'object', required: ['success', 'errorCode', 'message', 'data'], properties: {
        success: { type: 'boolean' }, errorCode: { type: 'string' }, message: { type: 'string' },
        data: { type: 'object', nullable: true, properties: { reason: { type: 'string' } } },
      },
    } } },
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
    schema: { params: idParams, body: emptyBody, description: 'Confirm a changed field. A changed record, model, task or classifier weights return 409 ERR_PROPOSAL_STALE. An identical value returns 409 ERR_PROPOSAL_NO_CHANGE. Success reports changed=true after Core advances the record version.' },
  }, async request => ok(await services.fields.execute(await owner(request), request.params.id)));
  app.post<{ Params: { id: string } }>('/assistant-api/v1/me/proposals/:id/cancel', {
    schema: { params: idParams, body: emptyBody },
  }, async request => {
    const identity = await owner(request);
    await services.records.proposal(identity, request.params.id);
    services.store.cancel(identity, request.params.id); return ok(null);
  });
}
