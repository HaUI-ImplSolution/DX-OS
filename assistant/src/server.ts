// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import Fastify from 'fastify';
import swagger from '@fastify/swagger';
import { readFileSync, openSync, closeSync, fstatSync, constants } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';
import { ApiError } from './errors.js';
import { identityVerifier } from './auth/identity.js';
import { Store } from './storage/store.js';
import { CoreSession } from './auth/core-session.js';
import { CoreClient } from './tools/core-client.js';
import { Records } from './tools/records.js';
import { loadClassifiers } from './classifier/index.js';
import { FieldProposals } from './tools/fields.js';
import { fieldRoutes } from './accounts/routes.js';

export async function createServer(store: Store, verify: ReturnType<typeof identityVerifier>,
  options?: { session: CoreSession; publicUrl: URL; core?: CoreClient; fields?: FieldProposals; records?: Records }) {
  const app = Fastify({ bodyLimit: 32768, requestTimeout: 60000, logger: false });
  await app.register(swagger, { openapi: {
    info: { title: 'DX-OS Assistant API', version: '0.1.0',
      description: 'Tasks run on classifier weights loaded by the service. ASSISTANT_CLASSIFIERS_DIR selects the weights directory at startup (default: /app/classifiers in the assistant image).',
    },
    components: { securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } } },
    security: [{ bearerAuth: [] }],
  }, transform: ({ schema, url }) => ({ schema: { ...schema, hide: url.startsWith('/internal/') }, url }) });
  app.setErrorHandler((error, _request, reply) => {
    const known = error instanceof ApiError;
    const validation = !known && typeof error === 'object' && error !== null && 'validation' in error;
    const frameworkStatus = typeof error === 'object' && error !== null && 'statusCode' in error
      && typeof error.statusCode === 'number' ? error.statusCode : 0;
    const clientError = validation || (frameworkStatus >= 400 && frameworkStatus < 500);
    const status = known ? error.status : validation ? 400 : clientError ? frameworkStatus : 500;
    return reply.code(status).send({ success: false,
      errorCode: known ? error.errorCode : status === 413 ? 'ERR_REQUEST_TOO_LARGE'
        : clientError ? 'ERR_INVALID_REQUEST' : 'ERR_INTERNAL',
      message: known ? error.message : status === 413 ? 'Request body is too large'
        : clientError ? 'Invalid request' : 'The request could not be completed',
      data: known && error.reason ? { reason: error.reason } : null });
  });
  app.get('/health', async () => ({ status: 'UP' }));
  app.get('/assistant-api/v1/features', async () => ({ enabled: true }));
  app.get('/assistant-api/v1/openapi.json', async request => {
    await verify(request.headers.authorization);
    return app.swagger();
  });
  app.get('/assistant-api/v1/me', { schema: {
    description: 'Account link and loaded classifiers. The assistant is configured when at least one classifier is loaded.',
    response: { 200: { type: 'object', properties: {
      success: { type: 'boolean' }, message: { type: 'string' }, data: { type: 'object', properties: {
        linked: { type: 'boolean' }, admin: { type: 'boolean' }, model: { type: 'object', properties: {
          configured: { type: 'boolean' },
          classifiers: { type: 'array', items: { type: 'string' }, description: 'Names of the loaded classifier weights' },
        } },
      } },
    } } },
  } }, async request => {
    const owner = await verify(request.headers.authorization);
    return { success: true, message: 'OK', data: {
      linked: !!store.getSecret(owner, 'core'),
      model: options?.fields?.status() ?? { configured: false, classifiers: [] },
      admin: owner.admin,
    } };
  });
  if (options) {
    if (options.fields && options.records) {
      fieldRoutes(app, { fields: options.fields, records: options.records, store, publicUrl: options.publicUrl, verify });
    }
    const cookiePath = '/assistant-api/v1/oauth/callback';
    const cookie = (value: string, maxAge: number) => `dxos_assistant_link=${value}; Path=${cookiePath}; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${options.publicUrl.protocol === 'https:' ? '; Secure' : ''}`;
    app.post('/assistant-api/v1/me/core/link', async (request, reply) => {
      if (request.headers.origin !== options.publicUrl.origin)
        throw new ApiError(403, 'ERR_ORIGIN', 'The request origin is not allowed');
      const owner = await verify(request.headers.authorization);
      const link = options.session.start(owner);
      reply.header('Set-Cookie', cookie(link.cookie, 300));
      return { success: true, message: 'OK', data: { url: link.url } };
    });
    app.delete('/assistant-api/v1/me/core/link', async request => {
      if (request.headers.origin !== options.publicUrl.origin)
        throw new ApiError(403, 'ERR_ORIGIN', 'The request origin is not allowed');
      const owner = await verify(request.headers.authorization);
      return { success: true, message: 'OK', data: await options.session.unlink(owner) };
    });
    app.get<{ Querystring: { code: string; state: string } }>('/assistant-api/v1/oauth/callback', {
      schema: { querystring: { type: 'object', required: ['code', 'state'], properties: {
        code: { type: 'string', minLength: 1, maxLength: 4096 },
        state: { type: 'string', minLength: 20, maxLength: 128 },
      } } },
    }, async (request, reply) => {
      const browserCookie = request.headers.cookie?.split(';').map(item => item.trim())
        .find(item => item.startsWith('dxos_assistant_link='))?.slice('dxos_assistant_link='.length) ?? '';
      reply.header('Set-Cookie', cookie('', 0));
      await options.session.callback(request.query.state, browserCookie, request.query.code);
      return reply.redirect(new URL('/assistant', options.publicUrl).toString());
    });
  }
  app.addHook('onClose', async () => store.close());
  return app;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const required = (name: string) => {
    const value = process.env[name];
    if (!value) throw new Error(`Missing configuration: ${name}`);
    return value;
  };
  const state = required('ASSISTANT_STATE_DIR');
  if (!isAbsolute(state) || state === '/') throw new Error('Use a private absolute state directory');
  const privateFile = (path: string) => {
    const descriptor = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
    try {
      const file = fstatSync(descriptor);
      if (!file.isFile() || (file.mode & 0o077) !== 0) throw new Error('Secret files must use mode 0600');
      return readFileSync(descriptor);
    } finally { closeSync(descriptor); }
  };
  const key = privateFile(required('ASSISTANT_MASTER_KEY_FILE'));
  const store = new Store(join(state, 'assistant.sqlite'), key);
  key.fill(0);
  const jwks = new URL(required('ASSISTANT_OIDC_JWKS_URL'));
  const issuer = required('ASSISTANT_OIDC_ISSUER');
  const verify = identityVerifier(issuer, required('ASSISTANT_OIDC_AUDIENCE'), jwks);
  const publicUrl = new URL(required('ASSISTANT_PUBLIC_URL'));
  const session = new CoreSession(store, { authorizationUrl: `${issuer}/protocol/openid-connect/auth`,
    tokenUrl: required('ASSISTANT_OIDC_TOKEN_URL'),
    revokeUrl: process.env.ASSISTANT_OIDC_REVOKE_URL ?? required('ASSISTANT_OIDC_TOKEN_URL').replace(/\/token$/, '/revoke'),
    clientId: required('ASSISTANT_OIDC_CLIENT_ID'),
    callbackUrl: new URL('/assistant-api/v1/oauth/callback', publicUrl).toString(),
  }, identityVerifier(issuer, required('ASSISTANT_CORE_AUDIENCE'), jwks));
  const core = new CoreClient(new URL(required('ASSISTANT_CORE_URL')), session);
  const records = new Records(core, store);
  const classifiers = loadClassifiers(process.env.ASSISTANT_CLASSIFIERS_DIR);
  const fields = new FieldProposals(records, core, store, classifiers);
  const app = await createServer(store, verify, { session, publicUrl, core, fields, records });
  const port = Number(required('ASSISTANT_PORT'));
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid assistant port');
  for (const signal of ['SIGTERM', 'SIGINT'] as const) {
    process.once(signal, () => { void app.close(); });
  }
  await app.listen({ host: required('ASSISTANT_HOST'), port });
}
