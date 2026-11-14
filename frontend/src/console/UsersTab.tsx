// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import {
  addUnit,
  createUser,
  getUser,
  getUserOptions,
  grantRole,
  listUserHistory,
  listUsers,
  removeUnit,
  resetUserPassword,
  revokeRole,
  setUserEnabled,
  type UserDetail,
  type UserHistoryItem,
  type UserHistoryPage,
  type UserOptions,
  type UserPage,
} from "./api";
import { formatDateTime } from "../shell/format";
import { t } from "../shell/t";

const SIZE = 20;
const HISTORY_SIZE = 20;

const message = (error: unknown) => (error instanceof Error ? error.message : t("Không xử lý được"));
const modelTitle = (name: string) => name.replace(/_/g, " ").replace(/^./, (first) => first.toUpperCase());

// What an audit record says was done, in words; an action that is not known shows its code.
function describe(item: UserHistoryItem, options: UserOptions): string {
  const meta = item.metadata ?? {};
  const text = (key: string) => String(meta[key] ?? "");
  const unit = () => {
    const name = text("unit");
    return options.units.find((u) => u.name === name)?.label || name;
  };
  switch (item.action) {
    case "USER_CREATED":
      return t("tạo tài khoản");
    case "USER_ROLE_GRANTED":
      return t("gán vai {role} của {model}", { role: text("role"), model: modelTitle(text("model")) });
    case "USER_ROLE_REVOKED":
      return t("gỡ vai {role} của {model}", { role: text("role"), model: modelTitle(text("model")) });
    case "USER_UNIT_ADDED":
      return t("thêm vào đơn vị {unit}", { unit: unit() });
    case "USER_UNIT_REMOVED":
      return t("bỏ khỏi đơn vị {unit}", { unit: unit() });
    case "USER_LOCKED":
      return t("khóa tài khoản");
    case "USER_UNLOCKED":
      return t("mở khóa tài khoản");
    case "USER_PASSWORD_RESET":
      return t("đặt lại mật khẩu");
    default:
      return item.action;
  }
}

// A password that is shown once, with a way to copy it.
function Secret({ title, password, onClose }: { title: string; password: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(password);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 1.5 }}>
          {t("Mật khẩu tạm này chỉ hiện một lần. Người dùng phải đổi mật khẩu ở lần đăng nhập đầu tiên.")}
        </Typography>
        <Typography component="code" sx={{ fontSize: 20, letterSpacing: 1, userSelect: "all" }}>
          {password}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={copy}>{copied ? t("Đã sao chép") : t("Sao chép")}</Button>
        <Button variant="contained" onClick={onClose}>
          {t("Đóng")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function CreateDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (username: string, password: string) => void }) {
  const [username, setUsername] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const created = await createUser(username, fullName, email);
      onCreated(created.username, created.temporaryPassword);
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t("Tạo người dùng")}</DialogTitle>
      <DialogContent sx={{ display: "grid", gap: 2, pt: "8px !important" }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label={t("Tên đăng nhập")} value={username} onChange={(e) => setUsername(e.target.value)} required />
        <TextField label={t("Họ và tên")} value={fullName} onChange={(e) => setFullName(e.target.value)} required />
        <TextField label="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Typography variant="caption" color="text.secondary">
          {t("Hệ thống tạo một mật khẩu tạm và hiện một lần sau khi tạo.")}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("Hủy")}</Button>
        <Button variant="contained" disabled={busy || !username.trim() || !fullName.trim()} onClick={submit}>
          {t("Tạo")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// One user: the roles by model and the units, each given or taken with a click.
function UserDialog({ id, options, onClose, onChanged }: { id: string; options: UserOptions; onClose: () => void; onChanged: () => void }) {
  const [detail, setDetail] = useState<UserDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [secret, setSecret] = useState<string | null>(null);
  const [history, setHistory] = useState<UserHistoryPage | null>(null);

  const load = useCallback(() => {
    getUser(id).then(setDetail, (e: unknown) => setError(message(e)));
  }, [id]);
  useEffect(load, [load]);

  // Page 0 replaces the list; a later page is added below it.
  const loadHistory = useCallback(
    (page: number) => {
      listUserHistory(id, page, HISTORY_SIZE).then(
        (loaded) => setHistory((current) => (page === 0 || !current ? loaded : { ...loaded, items: [...current.items, ...loaded.items] })),
        (e: unknown) => setError(message(e)),
      );
    },
    [id],
  );
  useEffect(() => {
    loadHistory(0);
  }, [loadHistory]);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      load();
      loadHistory(0);
      onChanged();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    setBusy(true);
    setError(null);
    try {
      setSecret((await resetUserPassword(id)).temporaryPassword);
      loadHistory(0);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  const held = detail?.roles ?? {};
  const declared = new Set(options.models.flatMap((m) => m.roles.map((r) => `${m.model}/${r.name}`)));
  const leftover = Object.entries(held).flatMap(([model, names]) =>
    names.filter((name) => !declared.has(`${model}/${name}`)).map((name) => ({ model, name })),
  );

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{detail ? `${detail.user.name} (${detail.user.username})` : t("Người dùng")}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {detail && (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {detail.user.email || t("Chưa có email")} · {detail.user.enabled ? t("Đang hoạt động") : t("Đã khóa")}
            </Typography>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
              {t("Vai theo ứng dụng")}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t("Bấm vào một vai để gán hoặc gỡ. Vai và đơn vị mới có hiệu lực khi phiên của người đó tự làm mới, tối đa khoảng 5 phút, hoặc khi người đó đăng nhập lại.")}
            </Typography>
            {options.models.map((m) => (
              <Box key={m.model} sx={{ mt: 1.5 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
                  {modelTitle(m.model)}
                </Typography>
                <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap" }}>
                  {m.roles.map((r) => {
                    const has = held[m.model]?.includes(r.name) ?? false;
                    return (
                      <Chip
                        key={r.name}
                        label={r.name}
                        title={r.description}
                        color={has ? "primary" : "default"}
                        variant={has ? "filled" : "outlined"}
                        disabled={busy}
                        onClick={() => run(() => (has ? revokeRole(id, m.model, r.name) : grantRole(id, m.model, r.name)))}
                      />
                    );
                  })}
                </Box>
              </Box>
            ))}
            {leftover.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
                  {t("Vai mô hình không còn khai báo")}
                </Typography>
                <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap" }}>
                  {leftover.map((r) => (
                    <Chip
                      key={`${r.model}/${r.name}`}
                      label={`${modelTitle(r.model)}: ${r.name}`}
                      color="warning"
                      disabled={busy}
                      onDelete={() => run(() => revokeRole(id, r.model, r.name))}
                    />
                  ))}
                </Box>
              </Box>
            )}
            <Typography variant="subtitle2" sx={{ mt: 3, mb: 0.5 }}>
              {t("Đơn vị")}
            </Typography>
            {options.units.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t("Chưa có mô hình nào khai báo đơn vị.")}
              </Typography>
            ) : (
              <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap" }}>
                {options.units.map((u) => {
                  const has = detail.units.includes(u.name);
                  return (
                    <Chip
                      key={u.name}
                      label={u.label || u.name}
                      title={u.name}
                      color={has ? "primary" : "default"}
                      variant={has ? "filled" : "outlined"}
                      disabled={busy}
                      onClick={() => run(() => (has ? removeUnit(id, u.name) : addUnit(id, u.name)))}
                    />
                  );
                })}
              </Box>
            )}
            <Typography variant="subtitle2" sx={{ mt: 3, mb: 0.5 }}>
              {t("Lịch sử thay đổi")}
            </Typography>
            {history && history.items.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {t("Chưa có thay đổi nào được ghi lại.")}
              </Typography>
            )}
            {history && history.items.length > 0 && (
              <Box component="ul" sx={{ m: 0, p: 0, listStyle: "none", display: "grid", gap: 0.75 }}>
                {history.items.map((item) => (
                  <Box component="li" key={item.id} sx={{ display: "flex", gap: 1.5 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ minWidth: 120, pt: "2px" }}>
                      {formatDateTime(item.occurredAt)}
                    </Typography>
                    <Typography variant="body2">
                      {item.actorName || t("Không rõ")} {describe(item, options)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            )}
            {history && history.page + 1 < history.totalPages && (
              <Button size="small" sx={{ mt: 1 }} onClick={() => loadHistory(history.page + 1)}>
                {t("Xem thêm")}
              </Button>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        {detail && (
          <>
            <Button disabled={busy} onClick={reset}>
              {t("Đặt lại mật khẩu")}
            </Button>
            <Button color={detail.user.enabled ? "error" : "primary"} disabled={busy} onClick={() => run(() => setUserEnabled(id, !detail.user.enabled))}>
              {detail.user.enabled ? t("Khóa tài khoản") : t("Mở khóa")}
            </Button>
          </>
        )}
        <Button variant="contained" onClick={onClose}>
          {t("Đóng")}
        </Button>
      </DialogActions>
      {secret && <Secret title={t("Mật khẩu tạm mới")} password={secret} onClose={() => setSecret(null)} />}
    </Dialog>
  );
}

// The people of the system: who holds which role of which model, and the keeping of accounts.
export default function UsersTab() {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<UserPage | null>(null);
  const [options, setOptions] = useState<UserOptions | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<{ username: string; password: string } | null>(null);

  const load = useCallback(() => {
    listUsers(query, page, SIZE).then(
      (loaded) => {
        setData(loaded);
        setError(null);
      },
      (e: unknown) => setError(message(e)),
    );
  }, [query, page]);
  useEffect(load, [load]);
  useEffect(() => {
    getUserOptions().then(setOptions, (e: unknown) => setError(message(e)));
  }, []);

  const find = () => {
    setPage(0);
    setQuery(search.trim());
  };

  return (
    <Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box sx={{ display: "flex", gap: 1.5, mb: 2, flexWrap: "wrap" }}>
        <TextField
          size="small"
          label={t("Tìm theo tên, tên đăng nhập hoặc email")}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && find()}
          sx={{ width: { xs: "100%", sm: 380 } }}
        />
        <Button variant="outlined" onClick={find}>
          {t("Tìm")}
        </Button>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" onClick={() => setCreating(true)}>
          {t("Tạo người dùng")}
        </Button>
      </Box>
      <Card sx={{ overflowX: "auto" }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t("Tên đăng nhập")}</TableCell>
              <TableCell>{t("Họ và tên")}</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>{t("Trạng thái")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(data?.items ?? []).map((u) => (
              <TableRow key={u.id} hover sx={{ cursor: "pointer" }} onClick={() => setOpen(u.id)}>
                <TableCell>{u.username}</TableCell>
                <TableCell>{u.name}</TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell>
                  <Chip size="small" variant="outlined" color={u.enabled ? "success" : "default"} label={u.enabled ? t("Hoạt động") : t("Đã khóa")} />
                </TableCell>
              </TableRow>
            ))}
            {data && data.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: "center" }}>
                    {t("Không có người dùng nào khớp.")}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
      {data && data.totalPages > 1 && (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
          <Pagination count={data.totalPages} page={page + 1} onChange={(_, next) => setPage(next - 1)} />
        </Box>
      )}
      {open && options && <UserDialog id={open} options={options} onClose={() => setOpen(null)} onChanged={load} />}
      {creating && (
        <CreateDialog
          onClose={() => setCreating(false)}
          onCreated={(username, password) => {
            setCreating(false);
            setCreated({ username, password });
            load();
          }}
        />
      )}
      {created && <Secret title={t("Đã tạo {username}", { username: created.username })} password={created.password} onClose={() => setCreated(null)} />}
    </Box>
  );
}
