// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import {
  changeAppRole,
  getUserOptions,
  listApps,
  registerApp,
  removeApp,
  renewAppSecret,
  type AppClient,
  type AppSecret,
  type UserOptions,
} from "./api";
import { t } from "../shell/t";

const NAME = /^[a-z][a-z0-9_-]{1,40}$/;
const message = (error: unknown) => (error instanceof Error ? error.message : t("Không xử lý được"));

// A secret that is shown once, with a way to copy it.
function SecretDialog({ secret, onClose }: { secret: AppSecret; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(secret.secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{secret.clientId}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 1.5 }}>
          {t("Mã bí mật này chỉ hiện một lần và hệ thống không giữ lại. Ứng dụng dùng nó cùng mã ứng dụng để lấy token.")}
        </DialogContentText>
        <Typography component="code" sx={{ fontSize: 16, wordBreak: "break-all", userSelect: "all" }}>
          {secret.secret}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={copy}>{copied ? t("Đã sao chép") : t("Sao chép")}</Button>
        <Button onClick={onClose}>{t("Đóng")}</Button>
      </DialogActions>
    </Dialog>
  );
}

// The outside applications that call the API with a secret of their own and one role of one model.
export default function AppsTab() {
  const [apps, setApps] = useState<AppClient[] | null>(null);
  const [options, setOptions] = useState<UserOptions | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [model, setModel] = useState("");
  const [role, setRole] = useState("");
  const [busy, setBusy] = useState(false);
  const [secret, setSecret] = useState<AppSecret | null>(null);
  const [removing, setRemoving] = useState<AppClient | null>(null);
  const [changing, setChanging] = useState<AppClient | null>(null);
  const [newRole, setNewRole] = useState("");

  const load = useCallback(() => {
    listApps().then(setApps, (e: unknown) => setError(message(e)));
  }, []);

  useEffect(() => {
    load();
    getUserOptions().then(setOptions, (e: unknown) => setError(message(e)));
  }, [load]);

  const roles = options?.models.find((item) => item.model === model)?.roles ?? [];
  const valid = NAME.test(name) && model !== "" && role !== "";

  const act = async (work: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await work();
      load();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t("Một ứng dụng ngoài lấy token bằng mã ứng dụng và mã bí mật, và chỉ có đúng một vai của một mô hình. Nó không gọi được bảng điều khiển hay quản trị người dùng.")}
      </Alert>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 1.5 }}>
        <Button variant="contained" onClick={() => setAdding(true)}>
          {t("Đăng ký ứng dụng")}
        </Button>
      </Box>
      <Card>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t("Mã ứng dụng")}</TableCell>
              <TableCell>{t("Mô hình")}</TableCell>
              <TableCell>{t("Vai được cấp")}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {(apps ?? []).map((app) => (
              <TableRow key={app.name}>
                <TableCell>{app.clientId}</TableCell>
                <TableCell>{app.model}</TableCell>
                <TableCell>{app.role}</TableCell>
                <TableCell align="right">
                  <Button
                    size="small"
                    disabled={busy}
                    onClick={() => {
                      setChanging(app);
                      setNewRole(app.role);
                    }}
                  >
                    {t("Đổi vai")}
                  </Button>
                  <Button size="small" disabled={busy} onClick={() => act(async () => setSecret(await renewAppSecret(app.name)))}>
                    {t("Đổi mã bí mật")}
                  </Button>
                  <Button size="small" color="error" disabled={busy} onClick={() => setRemoving(app)}>
                    {t("Xóa")}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {apps?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: "center" }}>
                    {t("Chưa có ứng dụng ngoài nào.")}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
      <Dialog open={adding} onClose={() => !busy && setAdding(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t("Đăng ký ứng dụng")}</DialogTitle>
        <DialogContent sx={{ display: "grid", gap: 2, pt: "8px !important" }}>
          <TextField
            label={t("Tên ứng dụng")}
            size="small"
            value={name}
            error={name !== "" && !NAME.test(name)}
            helperText={t("Chữ thường, số, gạch ngang và gạch dưới; mã ứng dụng sẽ là dxos-app-<tên>")}
            onChange={(event) => setName(event.target.value)}
          />
          <TextField
            select
            label={t("Mô hình")}
            size="small"
            value={model}
            onChange={(event) => {
              setModel(event.target.value);
              setRole("");
            }}
          >
            {(options?.models ?? []).map((item) => (
              <MenuItem key={item.model} value={item.model}>
                {item.model}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t("Vai được cấp")} size="small" value={role} disabled={model === ""} onChange={(event) => setRole(event.target.value)}>
            {roles.map((item) => (
              <MenuItem key={item.name} value={item.name}>
                {item.name}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setAdding(false)}>
            {t("Hủy")}
          </Button>
          <Button
            disabled={busy || !valid}
            onClick={() =>
              act(async () => {
                const created = await registerApp(name, model, role);
                setAdding(false);
                setName("");
                setModel("");
                setRole("");
                setSecret(created);
              })
            }
          >
            {t("Đăng ký")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={changing !== null} onClose={() => !busy && setChanging(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{changing?.clientId}</DialogTitle>
        <DialogContent sx={{ display: "grid", gap: 2, pt: "8px !important" }}>
          <DialogContentText>{t("Mã ứng dụng và mã bí mật giữ nguyên; token đã cấp giữ vai cũ đến khi hết hạn.")}</DialogContentText>
          <TextField select label={t("Vai được cấp")} size="small" value={newRole} onChange={(event) => setNewRole(event.target.value)}>
            {(options?.models.find((item) => item.model === changing?.model)?.roles ?? []).map((item) => (
              <MenuItem key={item.name} value={item.name}>
                {item.name}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setChanging(null)}>
            {t("Hủy")}
          </Button>
          <Button
            disabled={busy || newRole === "" || newRole === changing?.role}
            onClick={() =>
              act(async () => {
                if (changing) await changeAppRole(changing.name, newRole);
                setChanging(null);
              })
            }
          >
            {t("Lưu")}
          </Button>
        </DialogActions>
      </Dialog>
      {secret && <SecretDialog secret={secret} onClose={() => setSecret(null)} />}
      <Dialog open={removing !== null} onClose={() => setRemoving(null)}>
        <DialogTitle>{removing?.clientId}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t("Ứng dụng này không lấy được token nữa; các hồ sơ nó đã tạo vẫn còn.")}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRemoving(null)}>{t("Hủy")}</Button>
          <Button
            color="error"
            disabled={busy}
            onClick={() =>
              act(async () => {
                if (removing) await removeApp(removing.name);
                setRemoving(null);
              })
            }
          >
            {t("Xóa")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
