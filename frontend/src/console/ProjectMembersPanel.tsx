// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { Alert, Autocomplete, Box, Button, Chip, CircularProgress, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { searchPeople } from "../admin/api";
import type { Person } from "../admin/types";
import { listProjectMembers, removeProjectMember, setProjectMember, type ProjectMember } from "./api";
import { t } from "../shell/t";

const MIN_SEARCH = 2;
const SEARCH_DELAY_MS = 300;
const message = (error: unknown) => (error instanceof Error ? error.message : t("Không xử lý được"));

// The members of one project: who is in its scope, and who administers it. An administrator of the
// project adds and removes members; only the platform administrator makes or unmakes a project
// administrator.
export default function ProjectMembersPanel({ project, global, onChanged }: { project: string; global: boolean; onChanged?: () => void }) {
  const [members, setMembers] = useState<ProjectMember[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [text, setText] = useState("");
  const [found, setFound] = useState<Person[]>([]);
  const [searching, setSearching] = useState(false);

  const load = useCallback(() => {
    listProjectMembers(project).then(setMembers, (e: unknown) => setError(message(e)));
  }, [project]);
  useEffect(() => {
    setMembers(null);
    setError(null);
    load();
  }, [load]);

  useEffect(() => {
    if (text.trim().length < MIN_SEARCH) {
      setFound([]);
      return;
    }
    let live = true;
    setSearching(true);
    const timer = setTimeout(() => {
      searchPeople(text.trim())
        .then((people) => live && setFound(people))
        .catch(() => live && setFound([]))
        .finally(() => live && setSearching(false));
    }, SEARCH_DELAY_MS);
    return () => {
      live = false;
      clearTimeout(timer);
      setSearching(false);
    };
  }, [text]);

  const act = async (work: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await work();
      load();
      onChanged?.();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  const taken = new Set((members ?? []).map((member) => member.userId));

  return (
    <Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t("Thành viên là những người nằm trong phạm vi của dự án; thuộc dự án không tự cấp quyền nào. Quản trị viên dự án thêm, bớt thành viên và gán vai của các mô hình trong dự án cho họ.")}
      </Alert>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Autocomplete<Person>
        size="small"
        options={found.filter((person) => !taken.has(person.id))}
        loading={searching}
        value={null}
        inputValue={text}
        disabled={busy}
        filterOptions={(all) => all}
        getOptionLabel={(person) => person.name}
        noOptionsText={text.trim().length < MIN_SEARCH ? t("Gõ ít nhất 2 ký tự để tìm") : t("Không có ai khớp")}
        onInputChange={(_, next, reason) => reason !== "reset" && setText(next)}
        onChange={(_, person) => {
          if (!person) return;
          setText("");
          void act(() => setProjectMember(project, person.id, "MEMBER"));
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            label={t("Thêm thành viên")}
            slotProps={{
              input: {
                ...params.InputProps,
                endAdornment: (
                  <>
                    {searching ? <CircularProgress size={16} /> : null}
                    {params.InputProps.endAdornment}
                  </>
                ),
              },
            }}
          />
        )}
        sx={{ mb: 2, maxWidth: 420 }}
      />
      <Box sx={{ overflowX: "auto" }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>{t("Tên đăng nhập")}</TableCell>
            <TableCell>{t("Họ và tên")}</TableCell>
            <TableCell>{t("Cấp")}</TableCell>
            <TableCell align="right" />
          </TableRow>
        </TableHead>
        <TableBody>
          {(members ?? []).map((member) => {
            const admin = member.level === "ADMIN";
            return (
              <TableRow key={member.userId}>
                <TableCell>{member.username}</TableCell>
                <TableCell>{member.name}</TableCell>
                <TableCell>
                  <Chip size="small" color={admin ? "primary" : "default"} variant={admin ? "filled" : "outlined"} label={admin ? t("Quản trị viên dự án") : t("Thành viên")} />
                </TableCell>
                <TableCell align="right">
                  {global && (
                    <Button size="small" disabled={busy} onClick={() => act(() => setProjectMember(project, member.userId, admin ? "MEMBER" : "ADMIN"))}>
                      {admin ? t("Hạ xuống thành viên") : t("Nâng lên quản trị viên")}
                    </Button>
                  )}
                  <Button size="small" color="error" disabled={busy || (admin && !global)} onClick={() => act(() => removeProjectMember(project, member.userId))}>
                    {t("Bỏ khỏi dự án")}
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
          {members?.length === 0 && (
            <TableRow>
              <TableCell colSpan={4}>
                <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: "center" }}>
                  {t("Dự án này chưa có thành viên.")}
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1.5 }}>
        {t("Bỏ một người khỏi dự án không gỡ các vai đã gán cho họ; hãy gỡ vai ở tab Người dùng nếu cần.")}
      </Typography>
    </Box>
  );
}
