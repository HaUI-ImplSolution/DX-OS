// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { createServer, request as httpRequest, type IncomingMessage, type Server } from 'node:http';
import { connect, type Socket } from 'node:net';
import { pathToFileURL } from 'node:url';

// Forward proxy for the assistant: only the host and port of the inference endpoint are reachable.
export type Allowed = { host: string; port: number };

export function allowedFrom(endpoint: string | undefined): Allowed | undefined {
  if (!endpoint) return undefined;
  const url = new URL(endpoint);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Invalid inference endpoint');
  return { host: url.hostname.toLowerCase(), port: Number(url.port || (url.protocol === 'https:' ? 443 : 80)) };
}

const PATHS = new Set(['/v1/chat/completions', '/completion']);
const same = (allowed: Allowed | undefined, host: string, port: number) =>
  !!allowed && allowed.host === host.toLowerCase().replace(/^\[|\]$/g, '') && allowed.port === port;

export function createEgress(allowed: Allowed | undefined): Server {
  const server = createServer((req, res) => {
    let target: URL;
    try { target = new URL(req.url ?? ''); } catch { res.writeHead(400).end(); return; }
    if (target.protocol !== 'http:' || req.method !== 'POST' || !PATHS.has(target.pathname) || target.search ||
        !same(allowed, target.hostname, Number(target.port || 80))) { res.writeHead(403).end(); return; }
    const upstream = httpRequest({ host: target.hostname, port: Number(target.port || 80), path: target.pathname,
      method: 'POST', headers: { 'content-type': req.headers['content-type'] ?? 'application/json',
        ...(req.headers.authorization ? { authorization: req.headers.authorization } : {}),
        ...(req.headers['content-length'] ? { 'content-length': req.headers['content-length'] } : {}) } }, answer => {
      res.writeHead(answer.statusCode ?? 502, { 'content-type': answer.headers['content-type'] ?? 'application/json' });
      answer.pipe(res);
    });
    upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end(); });
    res.on('close', () => upstream.destroy());
    req.pipe(upstream);
  });
  server.on('connect', (req: IncomingMessage, client: Socket, head: Buffer) => {
    const match = /^(.+):(\d{1,5})$/.exec(req.url ?? '');
    const host = match?.[1] ?? '', port = Number(match?.[2]);
    if (!match || !same(allowed, host, port)) { client.end('HTTP/1.1 403 Forbidden\r\n\r\n'); return; }
    const upstream = connect(port, host.replace(/^\[|\]$/g, ''), () => {
      client.write('HTTP/1.1 200 Connection Established\r\n\r\n');
      if (head.length) upstream.write(head);
      upstream.pipe(client); client.pipe(upstream);
    });
    const close = () => { upstream.destroy(); client.destroy(); };
    upstream.on('error', close); client.on('error', close);
    upstream.setTimeout(240000, close);
  });
  server.requestTimeout = 240000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.ASSISTANT_EGRESS_PORT ?? '3128');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid egress port');
  const server = createEgress(allowedFrom(process.env.ASSISTANT_INFERENCE_URL));
  for (const signal of ['SIGTERM', 'SIGINT'] as const) process.once(signal, () => { server.close(); server.closeAllConnections(); });
  server.listen(port, process.env.ASSISTANT_EGRESS_HOST ?? '0.0.0.0');
}
