// SPDX-License-Identifier: Apache-2.0
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { AuthRequest, exchangeCodeAsync } from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import { isFarmer, userFromAccessToken, type CurrentUser } from "./claims";
import {
  clientId,
  discovery,
  endRemoteSession,
  redirectUri,
  SCOPES,
  toStoredTokens,
} from "./oidc";
import { clearSession, loadTokens, onSessionCleared, saveTokens } from "./session";
import { getValidAccessToken } from "./tokens";

WebBrowser.maybeCompleteAuthSession();

type Status = "loading" | "signedOut" | "signedIn";

interface AuthContextValue {
  status: Status;
  user: CurrentUser | null;
  signingIn: boolean;
  notice: string | null;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const MESSAGES = {
  failed: "Không đăng nhập được, thử lại.",
  notFarmer: "Ứng dụng này dành cho nông dân.",
  expired: "Phiên đăng nhập đã hết hạn, hãy đăng nhập lại.",
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [signingIn, setSigningIn] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const signingOutRef = useRef(false);

  useEffect(() => {
    const unsubscribe = onSessionCleared(() => {
      setUser(null);
      setStatus("signedOut");
      if (!signingOutRef.current) setNotice(MESSAGES.expired);
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const stored = await loadTokens();
      const accessToken = stored ? await getValidAccessToken() : null;
      if (cancelled) return;
      if (!accessToken) {
        setStatus("signedOut");
        return;
      }
      const current = userFromAccessToken(accessToken);
      if (!isFarmer(current)) {
        signingOutRef.current = true;
        await endRemoteSession(stored?.refreshToken ?? null);
        await clearSession();
        signingOutRef.current = false;
        if (!cancelled) setNotice(MESSAGES.notFarmer);
        return;
      }
      setUser(current);
      setStatus("signedIn");
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(async () => {
    setSigningIn(true);
    setNotice(null);
    try {
      const request = new AuthRequest({
        clientId,
        redirectUri: redirectUri(),
        scopes: SCOPES,
        usePKCE: true,
      });
      const result = await request.promptAsync(discovery);
      if (result.type === "cancel" || result.type === "dismiss") return;
      if (result.type !== "success") {
        setNotice(MESSAGES.failed);
        return;
      }
      const response = await exchangeCodeAsync(
        {
          clientId,
          code: result.params.code,
          redirectUri: redirectUri(),
          extraParams: { code_verifier: request.codeVerifier ?? "" },
        },
        discovery,
      );
      const tokens = toStoredTokens(response);
      const current = userFromAccessToken(tokens.accessToken);
      if (!isFarmer(current)) {
        await endRemoteSession(tokens.refreshToken);
        setNotice(MESSAGES.notFarmer);
        return;
      }
      await saveTokens(tokens);
      setUser(current);
      setStatus("signedIn");
    } catch {
      setNotice(MESSAGES.failed);
    } finally {
      setSigningIn(false);
    }
  }, []);

  const signOut = useCallback(async () => {
    signingOutRef.current = true;
    const tokens = await loadTokens();
    await endRemoteSession(tokens?.refreshToken ?? null);
    await clearSession();
    signingOutRef.current = false;
    setNotice(null);
  }, []);

  const value = useMemo(
    () => ({ status, user, signingIn, notice, signIn, signOut }),
    [status, user, signingIn, notice, signIn, signOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth phải nằm trong AuthProvider");
  return context;
}
