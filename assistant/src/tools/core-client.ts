// SPDX-License-Identifier: Apache-2.0
import { ApiError } from '../errors.js';
import type { Owner } from '../storage/store.js';
import type { CoreSession } from '../auth/core-session.js';

export type Target = { model: string; entity: string; id: number };
export const targetPath = (target: Target) => {
  if (!/^[a-z][a-z0-9_]{0,39}$/.test(target.model) || !/^[a-z][a-z0-9_]{0,39}$/.test(target.entity) ||
      !Number.isSafeInteger(target.id) || target.id < 1)
    throw new ApiError(400, 'ERR_TARGET', 'Choose a record from the Core results');
  return `/data/${target.model}/${target.entity}/${target.id}`;
};

export class CoreClient {
  constructor(private readonly baseUrl: URL, private readonly session: CoreSession) {}

  async read<T>(owner: Owner, path: string): Promise<T> {
    return this.request<T>(owner, path);
  }

  async write<T>(owner: Owner, path: string, body: unknown): Promise<T> {
    return this.request<T>(owner, path, body);
  }

  async plan<T>(owner: Owner, yaml: string): Promise<T> {
    return this.request<T>(owner, '/models/plan', yaml, true);
  }

  private async request<T>(owner: Owner, path: string, body?: unknown, yaml = false): Promise<T> {
    if (!path.startsWith('/') || path.startsWith('//') || path.includes('..') || path.includes('\\'))
      throw new ApiError(400, 'ERR_TARGET', 'Invalid Core operation');
    const token = await this.session.accessToken(owner);
    const url = new URL(`${this.baseUrl.pathname.replace(/\/$/, '')}/api/v1${path}`, this.baseUrl.origin);
    let response: Response;
    try {
      response = await fetch(url, { method: body === undefined ? 'GET' : 'POST', redirect: 'error',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': yaml ? 'application/yaml' : 'application/json' },
        ...(body === undefined ? {} : { body: yaml ? String(body) : JSON.stringify(body) }), signal: AbortSignal.timeout(10000) });
    } catch {
      throw new ApiError(503, body === undefined ? 'ERR_CORE_UNAVAILABLE' : 'ERR_EXECUTION_UNCERTAIN',
        body === undefined ? 'Core is unavailable' : 'The result is unknown; check the record history before trying again');
    }
    let data: { success?: boolean; data?: T & { reason?: string }; errorCode?: string };
    try {
      const reader = response.body?.getReader();
      if (!reader) throw new Error('Missing body');
      const parts: Uint8Array[] = []; let bytes = 0;
      for (;;) {
        const next = await reader.read();
        if (next.done) break;
        bytes += next.value.byteLength;
        if (bytes > 1048576) { await reader.cancel(); throw new Error('Response too large'); }
        parts.push(next.value);
      }
      data = JSON.parse(Buffer.concat(parts).toString('utf8'));
    } catch {
      throw new ApiError(502, body === undefined ? 'ERR_CORE_RESPONSE' : 'ERR_EXECUTION_UNCERTAIN',
        body === undefined ? 'Core returned an invalid response' : 'The result is unknown; check the record history');
    }
    if (!response.ok || data.success !== true) {
      const code = typeof data.errorCode === 'string' && /^ERR_[A-Z_]+$/.test(data.errorCode) ? data.errorCode : 'ERR_CORE_RESPONSE';
      const reason = typeof data.data?.reason === 'string' && /^[A-Z_]{1,80}$/.test(data.data.reason) ? data.data.reason : undefined;
      throw new ApiError(response.ok ? 502 : response.status, code, 'Core refused this request', reason);
    }
    return data.data as T;
  }
}
