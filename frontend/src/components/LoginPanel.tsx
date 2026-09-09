import { useState } from "react";
import { login } from "../api";
import { setCurrentUser } from "../auth";
import type { LoginResponse } from "../types";

interface LoginPanelProps {
  onLoggedIn: (user: LoginResponse) => void;
}

export default function LoginPanel({ onLoggedIn }: LoginPanelProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const user = await login({ username, password });
      setCurrentUser(user);
      onLoggedIn(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đăng nhập thất bại");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-screen">
      <section className="section login-card">
        <header className="section-header">
          <div>
            <h2>Đăng nhập DX-Farm</h2>
            <p className="section-subtitle">
              Tài khoản do quản trị viên HTX tạo — chưa có thì đăng nhập bằng tài khoản admin mặc định.
            </p>
          </div>
        </header>
        <form className="inline-form" onSubmit={submit}>
          <label className="field-label">
            Tên đăng nhập
            <input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
          </label>
          <label className="field-label">
            Mật khẩu
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Đang đăng nhập…" : "Đăng nhập"}
            </button>
          </div>
        </form>
        {error && (
          <div className="banner banner-danger">
            <span>{error}</span>
          </div>
        )}
      </section>
    </div>
  );
}
