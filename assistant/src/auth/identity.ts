// SPDX-License-Identifier: Apache-2.0
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { ApiError } from '../errors.js';
import type { Owner } from '../storage/store.js';

export type Identity = Owner & { admin: boolean };

export function identityVerifier(issuer: string, audience: string, jwksUrl: URL) {
  const jwks = createRemoteJWKSet(jwksUrl, { timeoutDuration: 5000 });
  return async (authorization: string | undefined): Promise<Identity> => {
    if (!authorization?.startsWith('Bearer ') || authorization.length > 16384)
      throw new ApiError(401, 'ERR_UNAUTHORIZED', 'Authentication required');
    try {
      const { payload } = await jwtVerify(authorization.slice(7), jwks, {
        issuer, audience, algorithms: ['RS256'], requiredClaims: ['sub', 'exp', 'iat'],
      });
      if (!payload.sub || payload.sub.length > 255) throw new Error('Invalid subject');
      const realm = payload.realm_access as { roles?: unknown } | undefined;
      const roles = realm?.roles;
      return { issuer, subject: payload.sub, admin: Array.isArray(roles) && roles.includes('ADMIN') };
    } catch {
      throw new ApiError(401, 'ERR_UNAUTHORIZED', 'The session is invalid or expired');
    }
  };
}
