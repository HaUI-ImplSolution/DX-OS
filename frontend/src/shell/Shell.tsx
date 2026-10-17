import { useCallback, useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { getUnreadCount } from "./api";
import type { AppContext, AppUiModule } from "./appModule";
import Section from "./components/Section";
import Notifications from "./pages/Notifications";
import type { AppManifest, MenuEntry } from "./types";

interface ShellProps {
  context: AppContext;
  module: AppUiModule;
  error: string | null;
  onDismissError: () => void;
  onLogout: () => void;
}

const UNREAD_POLL_MS = 60_000;

function isAllowed(menu: MenuEntry[], menuPath: string, role: string): boolean {
  const entry = menu.find((item) => item.path === menuPath);
  return !entry || entry.roles.length === 0 || entry.roles.includes(role);
}

function visibleMenu(manifest: AppManifest, role: string): MenuEntry[] {
  return manifest.menu.filter((entry) => isAllowed(manifest.menu, entry.path, role));
}

function Forbidden() {
  return (
    <Section step="🚫" title="Không đủ quyền" subtitle="Vai của bạn không được dùng chức năng này.">
      {null}
    </Section>
  );
}

// Frame of every app: header, menu from the manifest, notification bell and the routes the app module declares.
export default function Shell({ context, module, error, onDismissError, onLogout }: ShellProps) {
  const { user, manifest, onError } = context;
  const location = useLocation();
  const [unread, setUnread] = useState(0);

  const refreshUnread = useCallback(() => {
    getUnreadCount()
      .then(setUnread)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    refreshUnread();
    const timer = window.setInterval(refreshUnread, UNREAD_POLL_MS);
    return () => window.clearInterval(timer);
  }, [refreshUnread, location.pathname]);

  const menu = visibleMenu(manifest, user.role);

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>{manifest.name}</h1>
          <p>{module.tagline}</p>
        </div>
        <div className="user-bar">
          <NavLink to="/notifications" className="bell">
            Thông báo{unread > 0 && <span className="bell-count">{unread > 99 ? "99+" : unread}</span>}
          </NavLink>
          <span>
            {user.fullName} ({module.roleLabels[user.role] ?? user.role})
          </span>
          <button className="btn" onClick={onLogout}>
            Đăng xuất
          </button>
        </div>
        <nav className="tabs">
          {menu.map((entry) => (
            <NavLink
              key={entry.id}
              to={entry.path}
              end={entry.path === "/"}
              className={({ isActive }) => (isActive ? "tab tab-active" : "tab")}
            >
              {entry.label}
            </NavLink>
          ))}
        </nav>
      </header>

      {error && (
        <div className="banner banner-danger banner-dismissible">
          <span>{error}</span>
          <button onClick={onDismissError}>✕</button>
        </div>
      )}

      <Routes>
        {module.routes.map((route) => (
          <Route
            key={route.path}
            path={route.path}
            element={
              isAllowed(manifest.menu, route.menuPath ?? route.path, user.role) ? (
                route.render(context)
              ) : (
                <Forbidden />
              )
            }
          />
        ))}
        <Route
          path="/notifications"
          element={<Notifications
              onError={onError}
              onChanged={refreshUnread}
              pathOf={module.notificationPath}
            />}
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
