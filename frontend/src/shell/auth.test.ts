// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { beforeEach, describe, expect, it, vi } from "vitest";

const oidc = vi.hoisted(() => ({
  getUser: vi.fn(),
  signinRedirect: vi.fn(async () => undefined),
  signoutRedirect: vi.fn(async () => undefined),
  signinRedirectCallback: vi.fn(),
  removeUser: vi.fn(async () => undefined),
  addUserSignedOut: vi.fn(),
  removeUserSignedOut: vi.fn(),
}));
const clearInboxReviews = vi.hoisted(() => vi.fn());

vi.mock("oidc-client-ts", () => ({
  UserManager: class {
    getUser = oidc.getUser;
    signinRedirect = oidc.signinRedirect;
    signoutRedirect = oidc.signoutRedirect;
    signinRedirectCallback = oidc.signinRedirectCallback;
    removeUser = oidc.removeUser;
    events = { addUserSignedOut: oidc.addUserSignedOut, removeUserSignedOut: oidc.removeUserSignedOut };
  },
  WebStorageStateStore: class {},
}));
vi.mock("../assistant/inboxReview", () => ({ clearInboxReviews }));

// The module reads the browser address when it loads, so the browser is in place before it is imported.
const { session, location, replaceState } = vi.hoisted(() => {
  const session = new Map<string, string>();
  const location = { origin: "http://app", pathname: "/", search: "" };
  const replaceState = vi.fn();
  vi.stubGlobal("window", {
    location,
    sessionStorage: {
      getItem: (key: string) => session.get(key) ?? null,
      setItem: (key: string, value: string) => session.set(key, value),
      removeItem: (key: string) => session.delete(key),
    },
    history: { replaceState },
  });
  vi.stubGlobal("document", { title: "DX-OS" });
  return { session, location, replaceState };
});

import * as auth from "./auth";

// A token carries its claims in the middle part, in the URL-safe base64 form.
function token(claims: object) {
  const part = btoa(JSON.stringify(claims)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `header.${part}.signature`;
}

function oidcUser(overrides: Record<string, unknown> = {}) {
  return {
    expired: false,
    access_token: token({ realm_access: { roles: ["ADMIN", "STAFF"] } }),
    profile: { sub: "u-1", preferred_username: "an", name: "Nguyễn An" },
    ...overrides,
  };
}

beforeEach(() => {
  Object.values(oidc).forEach((fn) => {
    fn.mockReset();
  });
  oidc.signinRedirect.mockResolvedValue(undefined);
  oidc.signoutRedirect.mockResolvedValue(undefined);
  oidc.removeUser.mockResolvedValue(undefined);
  clearInboxReviews.mockReset();
  replaceState.mockReset();
  session.clear();
  location.pathname = "/";
  location.search = "";
});

describe("the signed-in user", () => {
  it("is read from the profile and the realm roles of the access token", async () => {
    oidc.getUser.mockResolvedValue(oidcUser());
    expect(await auth.getCurrentUser()).toEqual({ id: "u-1", username: "an", fullName: "Nguyễn An", roles: ["ADMIN", "STAFF"] });
    expect(auth.getCachedUser()?.username).toBe("an");
  });

  it("falls back to the username for the name and to no roles", async () => {
    oidc.getUser.mockResolvedValue(oidcUser({ access_token: token({}), profile: { sub: "u-2", preferred_username: "binh" } }));
    expect(await auth.getCurrentUser()).toEqual({ id: "u-2", username: "binh", fullName: "binh", roles: [] });
  });

  it("is nobody when the session ended or has expired", async () => {
    oidc.getUser.mockResolvedValue(oidcUser({ expired: true }));
    expect(await auth.getCurrentUser()).toBeNull();
    expect(auth.getCachedUser()).toBeNull();
    oidc.getUser.mockResolvedValue(null);
    expect(await auth.getCurrentUser()).toBeNull();
  });
});

describe("the token", () => {
  it("is the access token while it is valid", async () => {
    const user = oidcUser();
    oidc.getUser.mockResolvedValue(user);
    expect(await auth.getToken()).toBe(user.access_token);
  });

  it("is nothing once it has expired or there is no user", async () => {
    oidc.getUser.mockResolvedValue(oidcUser({ expired: true }));
    expect(await auth.getToken()).toBeNull();
    oidc.getUser.mockResolvedValue(null);
    expect(await auth.getToken()).toBeNull();
  });
});

describe("signing in and out", () => {
  it("remembers the page that asked for the sign-in and gives it back once", async () => {
    location.pathname = "/console/models";
    location.search = "?tab=1";
    await auth.signIn();
    expect(oidc.signinRedirect).toHaveBeenCalled();
    expect(auth.takeReturnPath()).toBe("/console/models?tab=1");
    expect(auth.takeReturnPath()).toBeNull();
  });

  it("remembers nothing when the sign-in starts at the front page", async () => {
    await auth.signIn();
    expect(auth.takeReturnPath()).toBeNull();
  });

  it("forgets the user and the reviews kept for the inbox on sign-out", async () => {
    oidc.getUser.mockResolvedValue(oidcUser());
    await auth.getCurrentUser();
    await auth.signOut();
    expect(auth.getCachedUser()).toBeNull();
    expect(clearInboxReviews).toHaveBeenCalled();
    expect(oidc.signoutRedirect).toHaveBeenCalled();
    expect(oidc.removeUser).not.toHaveBeenCalled();
  });

  it("clears the session without leaving the page", async () => {
    oidc.getUser.mockResolvedValue(oidcUser());
    await auth.getCurrentUser();
    auth.clearSession();
    expect(auth.getCachedUser()).toBeNull();
    expect(oidc.removeUser).toHaveBeenCalled();
    expect(clearInboxReviews).toHaveBeenCalled();
  });

  it("calls back when the session ends elsewhere, and stops when asked", () => {
    const callback = vi.fn();
    const off = auth.onSessionEnded(callback);
    const handler = oidc.addUserSignedOut.mock.calls[0][0] as () => void;
    handler();
    expect(callback).toHaveBeenCalledTimes(1);
    expect(oidc.removeUser).toHaveBeenCalled();
    off();
    expect(oidc.removeUserSignedOut).toHaveBeenCalledWith(handler);
  });
});

describe("the return from the sign-in page", () => {
  it("knows a redirect by its code and state", () => {
    location.search = "?code=abc&state=xyz";
    expect(auth.isCallbackRedirect()).toBe(true);
    location.search = "?code=abc";
    expect(auth.isCallbackRedirect()).toBe(false);
    location.search = "";
    expect(auth.isCallbackRedirect()).toBe(false);
  });

  it("reports why the sign-in failed, preferring the description", () => {
    location.search = "?error=access_denied&error_description=Account%20locked";
    expect(auth.callbackError()).toBe("Account locked");
    location.search = "?error=access_denied";
    expect(auth.callbackError()).toBe("access_denied");
    location.search = "";
    expect(auth.callbackError()).toBeNull();
  });

  it("takes the code and state off the address", () => {
    location.pathname = "/inbox";
    auth.clearCallbackParams();
    expect(replaceState).toHaveBeenCalledWith({}, "DX-OS", "/inbox");
  });

  it("finishes the sign-in, cleans the address and returns the user", async () => {
    oidc.signinRedirectCallback.mockResolvedValue(oidcUser());
    const user = await auth.handleCallback();
    expect(user?.username).toBe("an");
    expect(replaceState).toHaveBeenCalled();
  });
});
