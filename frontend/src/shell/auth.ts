import { UserManager, WebStorageStateStore, type User as OidcUser } from "oidc-client-ts";

// Empty in the packaged build: Keycloak is served by the gateway on the same origin as the app.
const KEYCLOAK_URL =
  import.meta.env.VITE_KEYCLOAK_URL || `${window.location.origin}/realms/dxos`;
const CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "dxos-web";

// The realm roles of the signed-in account; the shell picks the one that counts from the app manifest.
export interface CurrentUser {
  // Subject of the account: the id the backend stores on records the user owns.
  id: string;
  username: string;
  fullName: string;
  roles: string[];
}

const userManager = new UserManager({
  authority: KEYCLOAK_URL,
  client_id: CLIENT_ID,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  response_type: "code",
  scope: "openid profile",
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // Tự phát hiện khi phiên Keycloak kết thúc ở nơi khác (app khác, Keycloak Account Console) để
  // đăng xuất theo - single logout, xem sso/README.md.
  monitorSession: true,
});

// Cache đồng bộ để api.ts đăng xuất ngay khi gặp 401, không cần chờ Promise.
let cachedUser: CurrentUser | null = null;

// Claim "realm_access" chỉ có trong access token, không có trong ID token (oidcUser.profile).
function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const base64 = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(base64));
}

function toCurrentUser(oidcUser: OidcUser): CurrentUser | null {
  const claims = oidcUser.profile as Record<string, unknown>;
  const accessTokenClaims = decodeJwtPayload(oidcUser.access_token);
  const realmAccess = accessTokenClaims.realm_access as { roles?: string[] } | undefined;
  const preferredUsername = (claims.preferred_username as string) ?? "";
  return {
    id: (claims.sub as string) ?? "",
    username: preferredUsername,
    fullName: (claims.name as string) ?? preferredUsername,
    roles: realmAccess?.roles ?? [],
  };
}

function refreshCachedUser(oidcUser: OidcUser | null): CurrentUser | null {
  cachedUser = oidcUser && !oidcUser.expired ? toCurrentUser(oidcUser) : null;
  return cachedUser;
}

export function getCachedUser(): CurrentUser | null {
  return cachedUser;
}

export async function getCurrentUser(): Promise<CurrentUser | null> {
  return refreshCachedUser(await userManager.getUser());
}

export async function getToken(): Promise<string | null> {
  const oidcUser = await userManager.getUser();
  return oidcUser && !oidcUser.expired ? oidcUser.access_token : null;
}

const RETURN_PATH_KEY = "dxos.returnPath";

// Remembers the page that asked for a login, so the callback can return to it.
export function signIn(): Promise<void> {
  const path = window.location.pathname + window.location.search;
  if (path !== "/") window.sessionStorage.setItem(RETURN_PATH_KEY, path);
  return userManager.signinRedirect();
}

export function takeReturnPath(): string | null {
  const path = window.sessionStorage.getItem(RETURN_PATH_KEY);
  window.sessionStorage.removeItem(RETURN_PATH_KEY);
  return path;
}

// signoutRedirect tự lấy id_token_hint rồi mới xoá user, nên không gọi removeUser trước đó.
export function signOut(): Promise<void> {
  cachedUser = null;
  return userManager.signoutRedirect();
}

export function clearSession() {
  cachedUser = null;
  void userManager.removeUser();
}

// Gọi callback khi session monitoring phát hiện phiên Keycloak đã kết thúc ở nơi khác.
export function onSessionEnded(callback: () => void): () => void {
  const handler = () => {
    clearSession();
    callback();
  };
  userManager.events.addUserSignedOut(handler);
  return () => userManager.events.removeUserSignedOut(handler);
}

export function isCallbackRedirect(): boolean {
  const params = new URLSearchParams(window.location.search);
  return params.has("code") && params.has("state");
}

// Keycloak redirect về kèm ?error=... khi đăng nhập thất bại (huỷ, tài khoản bị khoá...) - tách
// riêng để không auto-redirect lại vào vòng lặp.
export function callbackError(): string | null {
  const params = new URLSearchParams(window.location.search);
  return params.get("error_description") ?? params.get("error");
}

export function clearCallbackParams() {
  window.history.replaceState({}, document.title, window.location.pathname);
}

export async function handleCallback(): Promise<CurrentUser | null> {
  const oidcUser = await userManager.signinRedirectCallback();
  window.history.replaceState({}, document.title, window.location.pathname);
  return refreshCachedUser(oidcUser);
}
