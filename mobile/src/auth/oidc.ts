import { makeRedirectUri, type DiscoveryDocument, type TokenResponse } from "expo-auth-session";
import { KEYCLOAK_CLIENT_ID, KEYCLOAK_URL } from "@/config";
import type { StoredTokens } from "./session";

// Endpoints are built from the configured realm URL so a LAN address works without discovery.
export const discovery: DiscoveryDocument = {
  authorizationEndpoint: `${KEYCLOAK_URL}/protocol/openid-connect/auth`,
  tokenEndpoint: `${KEYCLOAK_URL}/protocol/openid-connect/token`,
  endSessionEndpoint: `${KEYCLOAK_URL}/protocol/openid-connect/logout`,
};

export const clientId = KEYCLOAK_CLIENT_ID;

export const SCOPES = ["openid", "profile"];

// dxfarm://auth in a built app, exp://<host>/--/auth in Expo Go.
export const redirectUri = () => makeRedirectUri({ scheme: "dxfarm", path: "auth" });

export function toStoredTokens(
  response: TokenResponse,
  previousRefreshToken: string | null = null,
): StoredTokens {
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken ?? previousRefreshToken,
    expiresAt: (response.issuedAt + (response.expiresIn ?? 300)) * 1000,
  };
}

// Ends the Keycloak SSO session server-side; failures are ignored because local sign-out still applies.
export async function endRemoteSession(refreshToken: string | null) {
  if (!refreshToken) return;
  try {
    await fetch(discovery.endSessionEndpoint!, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `client_id=${encodeURIComponent(clientId)}&refresh_token=${encodeURIComponent(refreshToken)}`,
    });
  } catch {
    // Offline sign-out: the local session is already cleared.
  }
}
