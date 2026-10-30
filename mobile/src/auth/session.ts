// SPDX-License-Identifier: Apache-2.0
import * as SecureStore from "expo-secure-store";

const KEYS = {
  accessToken: "farm.accessToken",
  refreshToken: "farm.refreshToken",
  expiresAt: "farm.expiresAt",
} as const;

export interface StoredTokens {
  accessToken: string;
  refreshToken: string | null;
  // Epoch milliseconds at which the access token expires.
  expiresAt: number;
}

const listeners = new Set<() => void>();

// Called after the stored session is removed, whatever the cause.
export function onSessionCleared(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function loadTokens(): Promise<StoredTokens | null> {
  const [accessToken, refreshToken, expiresAt] = await Promise.all([
    SecureStore.getItemAsync(KEYS.accessToken),
    SecureStore.getItemAsync(KEYS.refreshToken),
    SecureStore.getItemAsync(KEYS.expiresAt),
  ]);
  if (!accessToken) return null;
  return { accessToken, refreshToken, expiresAt: Number(expiresAt) || 0 };
}

export async function saveTokens(tokens: StoredTokens) {
  await SecureStore.setItemAsync(KEYS.accessToken, tokens.accessToken);
  await SecureStore.setItemAsync(KEYS.expiresAt, String(tokens.expiresAt));
  if (tokens.refreshToken) {
    await SecureStore.setItemAsync(KEYS.refreshToken, tokens.refreshToken);
  } else {
    await SecureStore.deleteItemAsync(KEYS.refreshToken);
  }
}

export async function clearSession() {
  await Promise.all(Object.values(KEYS).map((key) => SecureStore.deleteItemAsync(key)));
  listeners.forEach((listener) => listener());
}
