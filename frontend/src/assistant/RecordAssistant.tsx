// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Box, Button, Card, CircularProgress, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { EditButton, useRecordContext, useRefresh } from "react-admin";
import { assistantRequest, useEntityAssistant, type Proposal } from "./api";
import { resourceName, type EntityMeta } from "../admin/types";
import { t } from "../shell/t";

type Result = { insufficient: boolean; proposal: Proposal | null };

function sourceText(text: string, entity: EntityMeta, units: Record<string, string>): string {
  const labels = new Map([...entity.fields.map(f => [f.name, f.label] as const), ...Object.entries(units)]);
  const escapeRegex = (name: string) => name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const names = [...labels.keys()].filter(name => name && labels.get(name) !== name).sort((a, b) => b.length - a.length);
  const readable = text.replace(/\b([a-z][a-z0-9_]*)\s*=\s*([\p{L}\p{N}_-]+)/gu, (whole, name: string, value: string) => {
    const field = entity.fields.find(f => f.name === name);
    return field ? `${field.label} ${t("là")} ${value}` : whole;
  });
  const result = names.length ? readable.replace(new RegExp(`\\b(${names.map(escapeRegex).join("|")})\\b`, "g"), name => labels.get(name)!) : readable;
  const unitLabels = [...new Set(Object.values(units))].filter(Boolean).sort((a, b) => b.length - a.length);
  return unitLabels.length ? result.replace(new RegExp(`(${unitLabels.map(escapeRegex).join("|")})[ \\t]*\\(\\1\\)`, "gi"), "$1") : result;
}

export default function RecordAssistant({ model, entity, units }: { model: string; entity: EntityMeta; units: Record<string, string> }) {
  const record = useRecordContext(); const refresh = useRefresh();
  const available = useEntityAssistant(entity);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  const [task, setTask] = useState(Object.keys(entity.aiTasks ?? {})[0] ?? "");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [value, setValue] = useState(""); const [busy, setBusy] = useState(false);
  const [error, setError] = useState(""); const [message, setMessage] = useState("");
  useEffect(() => { setProposal(null); setError(""); }, [model, entity.name, record?.id, record?.version, entity.aiTasks]);
  // The deadline is measured on this browser's clock from the time left on the server's clock.
  const deadline = useMemo(() => proposal ? Date.now() + proposal.expires - proposal.now : 0, [proposal?.id]);
  useEffect(() => {
    if (!proposal || proposal.state !== "pending") return;
    const id = proposal.id;
    const timer = window.setTimeout(() => setProposal(current => current?.id === id ? { ...current, state: "expired" } : current), Math.max(0, deadline - Date.now()));
    return () => window.clearTimeout(timer);
  }, [proposal?.id, proposal?.state, deadline]);
  const current = entity.aiTasks?.[task];
  const field = entity.fields.find(f => f.name === current?.outputField);
  const run = async (work: () => Promise<void>) => {
    setBusy(true); setError(""); setMessage("");
    try { await work(); } catch (e) {
      if (e instanceof Error && e.name === "AbortError") setMessage(t("Đã hủy lượt tạo đề xuất."));
      else setError(e instanceof Error ? e.message : t("Không thể hoàn tất yêu cầu."));
    }
    finally { setBusy(false); }
  };
  if (!available || !record || !entity.aiTasks || !Object.keys(entity.aiTasks).length) return null;
  const unchanged = !!proposal && value === proposal.snapshot.previousValue;
  const pending = proposal?.state === "pending" && deadline > Date.now();
  return <Card sx={{ p: 2 }}>
    <Typography variant="h6" gutterBottom>{t("Trợ lý nghiệp vụ")}</Typography>
    <Stack spacing={2}>
      <Typography variant="body2" color="text.secondary">{t("Xem căn cứ và xác nhận trước khi lưu đề xuất vào hồ sơ.")}</Typography>
      <TextField select label={t("Nhiệm vụ")} value={current ? task : ""} disabled={busy} onChange={e => { setTask(e.target.value); setProposal(null); setMessage(""); }}>
        {Object.entries(entity.aiTasks).map(([name, item]) => <MenuItem key={name} value={name}>{item.label}</MenuItem>)}
      </TextField>
      <Button disabled={busy || !current || !entity.canWrite} variant="outlined" onClick={() => void run(async () => {
        setProposal(null);
        active.current = new AbortController();
        const result = await assistantRequest<Result>("/me/proposals", "POST", { model, entity: entity.name, id: record.id, task }, active.current.signal);
        active.current = null;
        if (result.insufficient) { setMessage(t("Chưa đủ căn cứ để đưa ra đề xuất. Hãy kiểm tra thông tin hồ sơ.")); return; }
        setProposal(result.proposal); setValue(result.proposal?.snapshot.value ?? "");
      })}>{t("Tạo đề xuất")}</Button>
      {busy && active.current && <Button onClick={() => active.current?.abort()}>{t("Dừng tạo đề xuất")}</Button>}
      {busy && <Box role="status"><CircularProgress size={20} /><Typography component="span" sx={{ ml: 1 }}>{t("Đang xử lý…")}</Typography></Box>}
      {error && <Alert severity="error">{error}</Alert>}
      {message && <Alert severity="info">{message}</Alert>}
      {proposal?.state === "expired" && <Alert severity="info">{t("Đề xuất đã hết hạn. Hãy tạo đề xuất mới.")}</Alert>}
      {proposal && <>
        <Typography data-testid="assistant-proposed-value" sx={{ overflowWrap: "anywhere" }}><Box component="strong">{field?.label ?? t("Đề xuất")}: </Box>{value}</Typography>
        <Typography variant="body2" color="text.secondary">{t("Giá trị hiện tại")}: {String(proposal.snapshot.previousValue ?? "—")}</Typography>
        {unchanged && <Alert severity="info">{t("Giá trị đề xuất đã trùng với hồ sơ. Không có thay đổi để lưu.")}</Alert>}
        <TextField label={t("Điều chỉnh đề xuất")} value={value} disabled={busy || !pending}
          select={field?.type === "choice"} multiline={field?.type !== "choice"}
          onChange={e => setValue(e.target.value)}>
          {field?.type === "choice" && field.options?.map(option => <MenuItem key={option} value={option}>{option}</MenuItem>)}
        </TextField>
        {proposal.snapshot.warnings?.map(warning => <Alert key={warning.field} severity="warning">
          <strong>{t("Cần kiểm tra")} — {entity.fields.find(f => f.name === warning.field)?.label ?? warning.field}: </strong>{units[warning.expected] ?? warning.expected}
          <Typography variant="body2">{t("Giá trị hiện tại")}: {warning.actual === null ? "—" : units[warning.actual] ?? warning.actual}</Typography>
        </Alert>)}
        {!!proposal.snapshot.warnings?.length && <>
          <Typography variant="caption" color="text.secondary">{t("Xác nhận chỉ lưu trường đề xuất; các lưu ý không tự thay đổi hồ sơ.")}</Typography>
          {entity.canWrite && <EditButton resource={resourceName(model, entity.name)} record={record} label={t("Sửa hồ sơ theo lưu ý")} />}
        </>}
        {!!proposal.snapshot.sources?.length && <Box component="details">
          <Typography component="summary" sx={{ cursor: "pointer", color: "primary.main" }}>{t("Nguồn tham khảo")}</Typography>
          <Stack spacing={1} sx={{ mt: 1 }}>
            {proposal.snapshot.sources.map(source => <Box key={source.id}>
              <Typography variant="subtitle2">{source.title}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{sourceText(source.text, entity, units)}</Typography>
              <Typography variant="caption" color="text.secondary">{source.license} · {source.revision}</Typography>
            </Box>)}
          </Stack>
        </Box>}
        {pending && <>
          <Button disabled={busy || !value.trim() || unchanged} variant="contained" onClick={() => void run(async () => {
            if (value !== proposal.snapshot.value) {
              const revised = await assistantRequest<Proposal>(`/me/proposals/${proposal.id}/revise`, "POST", { value });
              setProposal(revised); setValue(revised.snapshot.value ?? "");
              setMessage(t("Đã cập nhật bản đề xuất. Kiểm tra lại rồi xác nhận lưu.")); return;
            }
            await assistantRequest(`/me/proposals/${proposal.id}/confirm`, "POST", {});
            setProposal(null); setMessage(t("Đã lưu thay đổi vào hồ sơ.")); refresh();
          })}>{unchanged ? t("Không có thay đổi") : value !== proposal.snapshot.value ? t("Xem lại nội dung đã sửa") : t("Xác nhận lưu")}</Button>
          <Button disabled={busy} onClick={() => void run(async () => {
            await assistantRequest(`/me/proposals/${proposal.id}/cancel`, "POST", {}); setProposal(null);
          })}>{t("Hủy đề xuất")}</Button>
        </>}
      </>}
    </Stack>
  </Card>;
}
