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
  DialogContentText,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { createProject, deleteProject, listProjects, updateProject, type Project } from "./api";
import { t } from "../shell/t";

const NAME = /^[a-z][a-z0-9_-]{0,39}$/;
const message = (error: unknown) => (error instanceof Error ? error.message : t("Không xử lý được"));

// The projects of this installation: each names the models and applications one integration uses.
export default function ProjectsTab() {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // null closes the dialog; a project edits it; "new" creates one.
  const [editing, setEditing] = useState<Project | "new" | null>(null);
  const [name, setName] = useState("");
  const [label, setLabel] = useState("");
  const [description, setDescription] = useState("");
  const [removing, setRemoving] = useState<Project | null>(null);

  const load = useCallback(() => {
    listProjects().then(setProjects, (e: unknown) => setError(message(e)));
  }, []);

  useEffect(load, [load]);

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

  const open = (project: Project | "new") => {
    setEditing(project);
    setName(project === "new" ? "" : project.name);
    setLabel(project === "new" ? "" : project.label);
    setDescription(project === "new" ? "" : (project.description ?? ""));
  };

  const creating = editing === "new";
  const valid = (!creating || NAME.test(name)) && label.trim() !== "";

  return (
    <Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t("Một dự án gom các mô hình và ứng dụng ngoài mà một hệ thống tích hợp dùng, để lọc và xem phạm vi ảnh hưởng. Dự án không cấp quyền và không tách dữ liệu giữa các nhóm.")}
      </Alert>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 1.5 }}>
        <Button variant="contained" onClick={() => open("new")}>
          {t("Tạo dự án")}
        </Button>
      </Box>
      <Card>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t("Dự án")}</TableCell>
              <TableCell>{t("Mô hình")}</TableCell>
              <TableCell>{t("Ứng dụng ngoài")}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {(projects ?? []).map((project) => (
              <TableRow key={project.name}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {project.label}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {project.name}
                    {project.description ? ` · ${project.description}` : ""}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {project.models.map((model) => (
                      <Chip key={model} size="small" variant="outlined" label={model} />
                    ))}
                  </Box>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {project.clients.map((app) => (
                      <Chip key={app.name} size="small" variant="outlined" label={`${app.name} → ${app.model}`} />
                    ))}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" disabled={busy} onClick={() => open(project)}>
                    {t("Sửa")}
                  </Button>
                  <Button size="small" color="error" disabled={busy} onClick={() => setRemoving(project)}>
                    {t("Xóa")}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {projects?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: "center" }}>
                    {t("Chưa có dự án nào. Mô hình và ứng dụng ngoài chưa gán dự án vẫn dùng bình thường.")}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
      <Dialog open={editing !== null} onClose={() => !busy && setEditing(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{creating ? t("Tạo dự án") : t("Sửa dự án")}</DialogTitle>
        <DialogContent sx={{ display: "grid", gap: 2, pt: "8px !important" }}>
          <TextField
            label={t("Tên dự án")}
            size="small"
            value={name}
            disabled={!creating}
            error={creating && name !== "" && !NAME.test(name)}
            helperText={t("Chữ thường, số, gạch ngang và gạch dưới; không đổi được sau khi tạo")}
            onChange={(event) => setName(event.target.value)}
          />
          <TextField label={t("Tên hiển thị")} size="small" value={label} onChange={(event) => setLabel(event.target.value)} />
          <TextField label={t("Mô tả")} size="small" value={description} multiline minRows={2} onChange={(event) => setDescription(event.target.value)} />
        </DialogContent>
        <DialogActions>
          <Button disabled={busy} onClick={() => setEditing(null)}>
            {t("Hủy")}
          </Button>
          <Button
            disabled={busy || !valid}
            onClick={() =>
              act(async () => {
                if (creating) await createProject(name, label.trim(), description);
                else if (editing) await updateProject(editing.name, label.trim(), description);
                setEditing(null);
              })
            }
          >
            {t("Lưu")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={removing !== null} onClose={() => setRemoving(null)}>
        <DialogTitle>{removing?.label}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {removing && (removing.models.length > 0 || removing.clients.length > 0)
              ? t("Dự án còn {models} mô hình và {clients} ứng dụng. Chuyển chúng sang dự án khác hoặc bỏ phân loại trước khi xóa.", {
                  models: removing.models.length,
                  clients: removing.clients.length,
                })
              : t("Xóa dự án này; không mô hình hay ứng dụng nào bị ảnh hưởng.")}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRemoving(null)}>{t("Hủy")}</Button>
          <Button
            color="error"
            disabled={busy}
            onClick={() =>
              act(async () => {
                if (removing) await deleteProject(removing.name);
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
