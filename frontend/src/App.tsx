// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
  callbackError,
  clearCallbackParams,
  getCurrentUser,
  handleCallback,
  isCallbackRedirect,
  onSessionEnded,
  signIn,
  takeReturnPath,
  type CurrentUser,
} from "./shell/auth";
import AdminApp from "./admin/AdminApp";
import { t } from "./shell/t";

function Notice({
  title,
  message,
  actions,
}: {
  title: string;
  message: string;
  actions: ReactNode;
}) {
  return (
    <div className="login-screen">
      <section className="section login-card">
        <header className="section-header">
          <div>
            <h2>{title}</h2>
            <p className="section-subtitle">{message}</p>
          </div>
        </header>
        <div className="form-actions">{actions}</div>
      </section>
    </div>
  );
}

export default function App() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    // A Keycloak callback exchanges its code for a token; otherwise the existing OIDC session is read.
    // A failed callback stops here instead of redirecting again, which would loop.
    (async () => {
      const failure = callbackError();
      if (failure) {
        clearCallbackParams();
        setAuthError(failure);
        setAuthReady(true);
        return;
      }
      try {
        const loggedInUser = isCallbackRedirect() ? await handleCallback() : await getCurrentUser();
        setUser(loggedInUser);
        const returnPath = loggedInUser ? takeReturnPath() : null;
        if (returnPath) navigate(returnPath, { replace: true });
      } finally {
        setAuthReady(true);
      }
    })();
  }, []);

  useEffect(() => {
    // Not signed in and no failure to show: go straight to Keycloak.
    if (authReady && !user && !authError) void signIn();
  }, [authReady, user, authError]);

  useEffect(() => {
    // Signing out in another app of the realm ends this session too (single logout).
    return onSessionEnded(() => setUser(null));
  }, []);

  if (!authReady || (!user && !authError)) {
    return <div className="login-screen" />;
  }

  if (authError) {
    return (
      <Notice
        title={t("Đăng nhập thất bại")}
        message={authError}
        actions={
          <button
            className="btn btn-primary"
            onClick={() => {
              setAuthError(null);
              void signIn();
            }}
          >
            {t("Thử lại")}
          </button>
        }
      />
    );
  }

  return <AdminApp />;
}
