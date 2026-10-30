// SPDX-License-Identifier: Apache-2.0
import { refreshAsync, TokenError } from "expo-auth-session";
import { clientId, discovery, toStoredTokens } from "./oidc";
import { clearSession, loadTokens, saveTokens, type StoredTokens } from "./session";

const EXPIRY_MARGIN_MS = 30_000;

let refreshing: Promise<string> | null = null;

async function refresh(tokens: StoredTokens & { refreshToken: string }): Promise<string> {
  try {
    const response = await refreshAsync({ clientId, refreshToken: tokens.refreshToken }, discovery);
    await saveTokens(toStoredTokens(response, tokens.refreshToken));
    return response.accessToken;
  } catch (error) {
    if (error instanceof TokenError) {
      await clearSession();
      throw error;
    }
    // Network failure: keep the session and let the request itself report the connectivity error.
    return tokens.accessToken;
  }
}

// Returns a usable access token, refreshing it when it is about to expire; null when signed out.
export async function getValidAccessToken(): Promise<string | null> {
  const tokens = await loadTokens();
  if (!tokens) return null;
  if (tokens.expiresAt - Date.now() > EXPIRY_MARGIN_MS) return tokens.accessToken;
  if (!tokens.refreshToken) {
    await clearSession();
    return null;
  }
  const refreshToken = tokens.refreshToken;
  refreshing ??= refresh({ ...tokens, refreshToken }).finally(() => {
    refreshing = null;
  });
  try {
    return await refreshing;
  } catch {
    return null;
  }
}
