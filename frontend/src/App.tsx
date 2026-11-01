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
  signOut,
  takeReturnPath,
  type CurrentUser,
} from "./shell/auth";

function Notice({ title, message, actions }: { title: string; message: string; actions: ReactNode }) {
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
    if (authReady && !user && !authError) void signIn();
  }, [authReady, user, authError]);

  useEffect(() => onSessionEnded(() => setUser(null)), []);

  if (!authReady || (!user && !authError)) return <div className="login-screen" />;

  if (authError) {
    return (
      <Notice
        title="Đăng nhập thất bại"
        message={authError}
        actions={
          <button className="btn btn-primary" onClick={() => { setAuthError(null); void signIn(); }}>
            Thử lại
          </button>
        }
      />
    );
  }

  return (
    <Notice
      title="DX-OS"
      message={`Đã đăng nhập với tài khoản ${user?.fullName ?? ""}.`}
      actions={
        <button className="btn btn-primary" onClick={() => void signOut()}>
          Đăng xuất
        </button>
      }
    />
  );
}
