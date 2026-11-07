// SPDX-License-Identifier: Apache-2.0
import { createHash, randomBytes } from 'node:crypto';
import { request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';
import type { Identity } from './identity.js';

type Tokens = { accessToken: string; refreshToken: string; expires: number };
type Config = { authorizationUrl: string; tokenUrl: string; clientId: string; callbackUrl: string };

export class CoreSession {
  private readonly refreshing = new Map<string, Promise<Tokens>>();
  constructor(private readonly store: Store, private readonly config: Config,
    private readonly verify: (authorization: string) => Promise<Identity>) {}

  start(owner: Owner) {
    const state = randomBytes(24).toString('base64url');
    const cookie = randomBytes(24).toString('base64url');
    const verifier = randomBytes(48).toString('base64url');
    this.store.createFlow(owner, state, cookie, verifier);
    const url = new URL(this.config.authorizationUrl);
    const params = { client_id: this.config.clientId, redirect_uri: this.config.callbackUrl,
      response_type: 'code', scope: 'openid profile', state,
      code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256' };
    for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
    return { url: url.toString(), cookie };
  }

  async callback(state: string, cookie: string, code: string): Promise<void> {
    const flow = this.store.takeFlow(state, cookie);
    const tokens = await this.exchange({ grant_type: 'authorization_code', code,
      code_verifier: flow.verifier, redirect_uri: this.config.callbackUrl });
    await this.checkOwner(flow.owner, tokens.accessToken);
    this.store.setSecret(flow.owner, 'core', tokens);
  }

  private async checkOwner(owner: Owner, token: string): Promise<void> {
    const identity = await this.verify(`Bearer ${token}`);
    if (identity.issuer !== owner.issuer || identity.subject !== owner.subject)
      throw new ApiError(403, 'ERR_LINK_IDENTITY', 'Sign in with the same account as DX-OS');
  }

  private async exchange(params: Record<string, string>): Promise<Tokens> {
    let response: { status: number; body: string };
    try {
      const url = new URL(this.config.tokenUrl), publicUrl = new URL(this.config.authorizationUrl);
      if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Invalid token endpoint');
      const body = new URLSearchParams({ ...params, client_id: this.config.clientId }).toString();
      response = await new Promise((resolve, reject) => {
        // Keycloak uses this host for the issuer when its public hostname is dynamic.
        const request = (url.protocol === 'https:' ? httpsRequest : httpRequest)(url, {
          method: 'POST', signal: AbortSignal.timeout(10000), headers: {
            Host: publicUrl.host, 'Content-Type': 'application/x-www-form-urlencoded',
            'Content-Length': Buffer.byteLength(body),
            'X-Forwarded-Host': publicUrl.host, 'X-Forwarded-Proto': publicUrl.protocol.slice(0, -1),
            'X-Forwarded-Port': publicUrl.port || (publicUrl.protocol === 'https:' ? '443' : '80'),
          },
        }, incoming => {
          const chunks: Buffer[] = []; let size = 0;
          incoming.on('data', chunk => {
            size += chunk.length;
            if (size > 65536) request.destroy(new Error('Invalid token response'));
            else chunks.push(chunk);
          });
          incoming.on('error', reject);
          incoming.on('end', () => resolve({ status: incoming.statusCode ?? 502, body: Buffer.concat(chunks).toString('utf8') }));
        });
        request.on('error', reject); request.end(body);
      });
    } catch {
      throw new ApiError(503, 'ERR_IDENTITY_UNAVAILABLE', 'The identity service is unavailable');
    }
    if (response.status < 200 || response.status >= 300) throw new ApiError(response.status === 400 || response.status === 401 ? 401 : 503,
      'ERR_CORE_LINK', 'Link your DX-OS account again');
    let data: Record<string, unknown>;
    try { data = JSON.parse(response.body) as Record<string, unknown>; }
    catch { throw new ApiError(502, 'ERR_IDENTITY_RESPONSE', 'The identity service returned an invalid session'); }
    if (typeof data.access_token !== 'string' || typeof data.refresh_token !== 'string' ||
        typeof data.expires_in !== 'number' || !Number.isFinite(data.expires_in) || data.expires_in <= 0)
      throw new ApiError(502, 'ERR_IDENTITY_RESPONSE', 'The identity service returned an invalid session');
    return { accessToken: data.access_token, refreshToken: data.refresh_token,
      expires: Date.now() + data.expires_in * 1000 };
  }

  async accessToken(owner: Owner): Promise<string> {
    const saved = this.store.getSecret<Tokens>(owner, 'core');
    if (!saved) throw new ApiError(401, 'ERR_CORE_LINK', 'Link your DX-OS account first');
    if (saved.expires > Date.now() + 30000) {
      await this.checkOwner(owner, saved.accessToken);
      return saved.accessToken;
    }
    const key = JSON.stringify(owner);
    let pending = this.refreshing.get(key);
    if (!pending) {
      pending = (async () => {
        try {
          const tokens = await this.exchange({ grant_type: 'refresh_token', refresh_token: saved.refreshToken });
          await this.checkOwner(owner, tokens.accessToken);
          this.store.setSecret(owner, 'core', tokens);
          return tokens;
        } catch (error) {
          if (error instanceof ApiError && error.status === 401) this.store.deleteSecret(owner, 'core');
          throw error;
        }
      })();
      this.refreshing.set(key, pending);
      void pending.finally(() => this.refreshing.delete(key)).catch(() => {});
    }
    return (await pending).accessToken;
  }
}
