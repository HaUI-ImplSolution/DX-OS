import { useCallback, useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useNavigate } from "react-router-dom";
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
} from "./auth";
import CaseDetail from "./pages/CaseDetail";
import DemoFlow from "./pages/DemoFlow";
import Households from "./pages/Households";
import Queue from "./pages/Queue";
import Requests from "./pages/Requests";

const ROLE_LABEL: Record<CurrentUser["role"], string> = {
  FARMER: "Nông dân",
  ENGINEER: "Kỹ sư",
  ADMIN: "Quản trị viên",
};

export default function App() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Token hết hạn -> api.ts đã xoá session OIDC (401), đồng bộ lại state để quay về màn đăng nhập.
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


  if (!authReady) {
    return <div className="login-screen" />;
  }

  if (authError) {
    return (
      <div className="login-screen">
        <section className="section login-card">
          <header className="section-header">
            <div>
              <h2>Đăng nhập thất bại</h2>
              <p className="section-subtitle">{authError}</p>
            </div>
          </header>
          <div className="form-actions">
            <button
              className="btn btn-primary"
              onClick={() => {
                setAuthError(null);
                void signIn();
              }}
            >
              Thử lại
            </button>
          </div>
        </section>
      </div>
    );
  }

  if (!user) {
    // Đang tự redirect sang Keycloak (xem effect ở trên) - không cần render gì thêm.
    return <div className="login-screen" />;
  }

  if (user.role === "FARMER") {
    return (
      <div className="login-screen">
        <section className="section login-card">
          <header className="section-header">
            <div>
              <h2>Vui lòng dùng ứng dụng di động</h2>
              <p className="section-subtitle">
                Tài khoản nông dân ({user.fullName}) chỉ sử dụng được trên ứng dụng DX-Farm di động.
                Trang web dành cho kỹ sư và quản trị viên.
              </p>
            </div>
          </header>
          <div className="form-actions">
            <button className="btn btn-primary" onClick={logout}>
              Đăng xuất
            </button>
          </div>
        </section>
      </div>
    );
  }

  const staff = user.role === "ENGINEER" || user.role === "ADMIN";

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>DX-Farm</h1>
          <p>Quản lý thửa ruộng, vụ mùa và chẩn đoán sâu bệnh của hợp tác xã</p>
        </div>
        <div className="user-bar">
          <span>
            {user.fullName} ({ROLE_LABEL[user.role]})
          </span>
          <button className="btn" onClick={logout}>
            Đăng xuất
          </button>
        </div>
        <nav className="tabs">
          <NavLink to="/" end className={({ isActive }) => (isActive ? "tab tab-active" : "tab")}>
            Luồng demo
          </NavLink>
          {staff && (
            <NavLink to="/requests" className={({ isActive }) => (isActive ? "tab tab-active" : "tab")}>
              Yêu cầu
            </NavLink>
          )}
          {user.role === "ADMIN" && (
            <NavLink to="/households" className={({ isActive }) => (isActive ? "tab tab-active" : "tab")}>
              Nông hộ
            </NavLink>
          )}
          {staff && (
            <NavLink to="/queue" className={({ isActive }) => (isActive ? "tab tab-active" : "tab")}>
              Hàng đợi chẩn đoán
            </NavLink>
          )}
        </nav>
      </header>

      {error && (
        <div className="banner banner-danger banner-dismissible">
          <span>{error}</span>
          <button onClick={() => setError(null)}>✕</button>
        </div>
      )}

      <Routes>
        <Route path="/" element={<DemoFlow role={user.role} onError={showError} />} />
        <Route path="/requests" element={<Requests role={user.role} onError={showError} />} />
        <Route path="/requests/:caseId" element={<CaseDetail role={user.role} onError={showError} />} />
        {user.role === "ADMIN" && <Route path="/households" element={<Households onError={showError} />} />}
        <Route path="/queue" element={<Queue role={user.role} onError={showError} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
