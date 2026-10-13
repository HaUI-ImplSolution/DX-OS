import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import appRegistry from "./appRegistry";
import { ApiError, getAppManifest } from "./shell/api";
import type { AppContext, AppUiModule } from "./shell/appModule";
import {
  callbackError,
  clearCallbackParams,
  getCachedUser,
  getCurrentUser,
  handleCallback,
  isCallbackRedirect,
  onSessionEnded,
  signIn,
  signOut,
  takeReturnPath,
  type CurrentUser,
} from "./shell/auth";
import { registerErrorMessages } from "./shell/errorMessages";
import { registerLabels } from "./shell/labels";
import Shell from "./shell/Shell";
import type { AppManifest } from "./shell/types";

interface LoadedApp {
  manifest: AppManifest;
  module: AppUiModule;
}

type AppState =
  | { phase: "loading" }
  | { phase: "failed"; title: string; message: string; retry: boolean }
  | { phase: "ready"; app: LoadedApp };

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

// The strongest role the account holds among the roles the app defines (roles are listed weakest first).
function pickRole(manifest: AppManifest, accountRoles: string[]): string | null {
  const held = manifest.roles.filter((role) => accountRoles.includes(role));
  return held.length > 0 ? held[held.length - 1] : null;
}

export default function App() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [appState, setAppState] = useState<AppState>({ phase: "loading" });

  // Token hết hạn -> api đã xoá session OIDC (401), đồng bộ lại state để quay về màn đăng nhập.
  const showError = useCallback((message: string) => {
    setError(message);
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (!getCachedUser()) setUser(null);
  }, []);

  const logout = () => {
    void signOut();
  };

  useEffect(() => {
    // Callback từ Keycloak thì đổi code lấy token, ngược lại đọc session OIDC hiện có.
    // Callback lỗi (huỷ đăng nhập, tài khoản bị khoá...) thì dừng lại báo lỗi, không auto-redirect
    // lại kẻo lặp vô hạn.
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
        // Back to the page that sent the user to the login screen.
        const returnPath = loggedInUser ? takeReturnPath() : null;
        if (returnPath) navigate(returnPath, { replace: true });
      } finally {
        setAuthReady(true);
      }
    })();
  }, []);

  useEffect(() => {
    // Chưa đăng nhập (và không phải do vừa lỗi) -> tự redirect sang Keycloak ngay, không bắt
    // người dùng bấm thêm nút "Đăng nhập" - vào thẳng từ portal là vào được app sau khi qua SSO.
    if (authReady && !user && !authError) void signIn();
  }, [authReady, user, authError]);

  useEffect(() => {
    // Đăng xuất ở app khác (Superset) hoặc Keycloak Account Console cũng kết thúc phiên ở đây -
    // single logout, xem sso/README.md.
    return onSessionEnded(() => setUser(null));
  }, []);

  // Once signed in, ask the backend which app it runs and load the web module for it.
  const username = user?.username;
  const [loadAttempt, setLoadAttempt] = useState(0);
  useEffect(() => {
    if (!username) return;
    let cancelled = false;
    setAppState({ phase: "loading" });
    (async () => {
      try {
        const manifest = await getAppManifest();
        const loader = appRegistry[manifest.id];
        if (!loader) {
          if (!cancelled) {
            setAppState({
              phase: "failed",
              title: "Chưa có giao diện cho ứng dụng này",
              message: `Backend đang chạy ứng dụng "${manifest.id}" nhưng web chưa có giao diện tương ứng.`,
              retry: false,
            });
          }
          return;
        }
        const module = (await loader()).default;
        registerLabels(module.labels ?? {});
        registerErrorMessages(module.errorMessages ?? {});
        document.title = manifest.name;
        if (!cancelled) setAppState({ phase: "ready", app: { manifest, module } });
      } catch (err) {
        // The gateway refuses every call of an account that may only use the mobile client.
        if (!cancelled && err instanceof ApiError && err.status === 403) {
          setAppState({
            phase: "failed",
            title: "Tài khoản không dùng được trên web",
            message: "Tài khoản này chỉ được dùng trên ứng dụng di động. Trang web dành cho các tài khoản quản lý.",
            retry: false,
          });
          return;
        }
        if (!cancelled) {
          setAppState({
            phase: "failed",
            title: "Không tải được ứng dụng",
            message: err instanceof Error ? err.message : "Không đọc được thông tin ứng dụng từ máy chủ.",
            retry: true,
          });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [username, loadAttempt]);

  if (!authReady) {
    return <div className="login-screen" />;
  }

  if (authError) {
    return (
      <Notice
        title="Đăng nhập thất bại"
        message={authError}
        actions={
          <button
            className="btn btn-primary"
            onClick={() => {
              setAuthError(null);
              void signIn();
            }}
          >
            Thử lại
          </button>
        }
      />
    );
  }

  if (!user) {
    // Đang tự redirect sang Keycloak (xem effect ở trên) - không cần render gì thêm.
    return <div className="login-screen" />;
  }

  if (appState.phase === "loading") {
    return (
      <div className="login-screen">
        <p className="section-subtitle">Đang tải ứng dụng…</p>
      </div>
    );
  }

  if (appState.phase === "failed") {
    return (
      <Notice
        title={appState.title}
        message={appState.message}
        actions={
          <>
            {appState.retry && (
              <button className="btn btn-primary" onClick={() => setLoadAttempt((n) => n + 1)}>
                Thử lại
              </button>
            )}
            <button className="btn" onClick={logout}>
              Đăng xuất
            </button>
          </>
        }
      />
    );
  }

  const { manifest, module } = appState.app;
  const role = pickRole(manifest, user.roles);

  if (!role) {
    return (
      <Notice
        title="Tài khoản chưa được cấp quyền"
        message={`Tài khoản ${user.fullName} chưa có vai nào trong ứng dụng ${manifest.name}.`}
        actions={
          <button className="btn btn-primary" onClick={logout}>
            Đăng xuất
          </button>
        }
      />
    );
  }

  const blocked = module.blockedRoles?.[role];
  if (blocked) {
    return (
      <Notice
        title={blocked.title}
        message={blocked.message(user.fullName)}
        actions={
          <button className="btn btn-primary" onClick={logout}>
            Đăng xuất
          </button>
        }
      />
    );
  }

  const context: AppContext = {
    user: { id: user.id, username: user.username, fullName: user.fullName, role },
    manifest,
    onError: showError,
  };

  return (
    <Shell
      context={context}
      module={module}
      error={error}
      onDismissError={() => setError(null)}
      onLogout={logout}
    />
  );
}
