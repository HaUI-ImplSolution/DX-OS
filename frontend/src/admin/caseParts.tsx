// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Avatar,
  Box,
  Button,
  Card,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  MenuItem,
  TextField as MuiTextField,
  Tooltip,
  Typography,
  type ButtonProps,
} from "@mui/material";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { useGetIdentity, useNotify, useRecordContext, useRefresh } from "react-admin";
import { translateBackend } from "../shell/backendMessages";
import { formatDateTime, timeAgo } from "../shell/format";
import { StatusChip, TONES } from "./StatusChip";
import { assignRecord, fileLink, listEvents, listFiles, listHandlers, removeFile, takeAction, uploadFile } from "./api";
import type { AttachmentItem, EntityMeta, Person, ProcessAction, RecordEvent } from "./types";

interface Props {
  model: string;
  entity: EntityMeta;
}

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");

// A titled card; a panel that has nothing to show returns null before it gets here.
export function Section({ title, action, children }: { title: string; action?: JSX.Element; children: React.ReactNode }) {
  return (
    <Card sx={{ p: 2.5 }}>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 1.5 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          {title}
        </Typography>
        {action}
      </Box>
      {children}
    </Card>
  );
}

// A decision reads by what it leads to: approving is the main button, rejecting is red.
function actionStyle(action: ProcessAction): Pick<ButtonProps, "variant" | "color"> {
  const tone = TONES[action.to];
  const color = tone === "success" || tone === "error" || tone === "warning" ? tone : "primary";
  return { variant: tone === "success" ? "contained" : "outlined", color };
}

// Withdrawing ends the record for good, so it asks for a confirmation and takes an optional reason.
const WITHDRAW = "withdraw";

// The actions of the process the signed-in user can take on this record now.
export function ActionButtons({ model, entity }: Props) {
  const record = useRecordContext();
  const { data: identity } = useGetIdentity();
  const notify = useNotify();
  const refresh = useRefresh();
  const [busy, setBusy] = useState(false);
  const [noteFor, setNoteFor] = useState<ProcessAction | null>(null);
  const [note, setNote] = useState("");
  const process = entity.process;
  if (!record || !process) return null;

  const me = identity?.id;
  const allowed = process.actions.filter((action) => {
    if (!action.from.includes(record.status)) return false;
    if (action.by === "OWNER") return record.created_by === me;
    if (!entity.canHandle) return false;
    if (!process.allowSelfDecision && record.created_by === me) return false;
    return !record.assignee || record.assignee === me;
  });

  const send = async (action: ProcessAction, text?: string) => {
    setBusy(true);
    try {
      await takeAction(model, entity.name, record.id, action.name, text);
      notify(`${action.label}: xong`, { type: "success" });
      setNoteFor(null);
      setNote("");
      refresh();
    } catch (error) {
      notify(message(error), { type: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {allowed.map((action) => (
        <Button
          key={action.name}
          {...actionStyle(action)}
          sx={{ ml: 1 }}
          disabled={busy}
          onClick={() => (action.needsNote || action.name === WITHDRAW ? setNoteFor(action) : send(action))}
        >
          {action.label}
        </Button>
      ))}
      <Dialog open={noteFor !== null} onClose={() => setNoteFor(null)} fullWidth>
        <DialogTitle>{noteFor?.label}</DialogTitle>
        <DialogContent>
          {noteFor?.name === WITHDRAW && (
            <DialogContentText sx={{ mb: 2 }}>Hồ sơ đã rút lại sẽ dừng xử lý và không nộp lại được.</DialogContentText>
          )}
          <MuiTextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={noteFor?.needsNote ? "Ghi chú" : "Lý do (không bắt buộc)"}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNoteFor(null)}>Hủy</Button>
          <Button
            disabled={busy || (noteFor?.needsNote === true && !note.trim())}
            onClick={() => noteFor && send(noteFor, note.trim() || undefined)}
          >
            {noteFor?.name === WITHDRAW ? "Rút lại" : "Gửi"}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

// Who holds this record, for the users who can assign it.
export function AssignPanel({ model, entity }: Props) {
  const record = useRecordContext();
  const { data: identity } = useGetIdentity();
  const notify = useNotify();
  const refresh = useRefresh();
  const [people, setPeople] = useState<Person[]>([]);
  const closed = entity.process?.states.find((state) => state.name === record?.status)?.terminal ?? false;
  const id = record?.id;

  useEffect(() => {
    if (entity.canAssign && id !== undefined && !closed) {
      listHandlers(model, entity.name, id).then(setPeople, () => setPeople([]));
    }
  }, [model, entity.name, entity.canAssign, id, closed]);

  if (!record || !entity.canAssign || closed) return null;

  const assign = async (assignee: string | null) => {
    try {
      await assignRecord(model, entity.name, record.id, assignee);
      refresh();
    } catch (error) {
      notify(message(error), { type: "error" });
    }
  };

  const me = identity?.id;
  const holder = people.find((person) => person.id === record.assignee)?.name;
  const canClaim = people.some((person) => person.id === me) && record.assignee !== me;
  return (
    <Section title="Người xử lý">
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 2 }}>
        <Avatar sx={{ width: 36, height: 36, bgcolor: record.assignee ? "primary.main" : "action.disabledBackground" }}>
          {record.assignee ? (holder ?? "?").charAt(0).toUpperCase() : "–"}
        </Avatar>
        <Box>
          <Typography sx={{ fontWeight: 600 }}>{record.assignee ? (holder ?? "Đã giao") : "Chưa giao ai"}</Typography>
          <Typography variant="caption" color="text.secondary">
            {record.assignee ? "Đang giữ hồ sơ này" : "Cả vai xử lý đều nhận được"}
          </Typography>
        </Box>
      </Box>
      <Box sx={{ display: "flex", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
        {canClaim && (
          <Button variant="contained" onClick={() => assign(String(me))}>
            Nhận việc
          </Button>
        )}
        <MuiTextField
          select
          size="small"
          sx={{ minWidth: 200, flex: 1 }}
          label="Giao cho"
          value={people.some((person) => person.id === record.assignee) ? record.assignee : ""}
          onChange={(event) => assign(event.target.value)}
        >
          {people.map((person) => (
            <MenuItem key={person.id} value={person.id}>
              {person.name}
            </MenuItem>
          ))}
        </MuiTextField>
        {record.assignee && <Button onClick={() => assign(null)}>Bỏ giao</Button>}
      </Box>
    </Section>
  );
}

const EVENT_LABELS: Record<string, string> = {
  created: "Tạo hồ sơ",
  updated: "Sửa thông tin",
  deleted: "Xóa",
  assigned: "Giao việc",
  file_added: "Thêm tệp",
  file_removed: "Gỡ tệp",
  overdue: "Quá hạn xử lý",
  completion_skipped: "Bỏ qua hành động hoàn tất",
  auto_closed: "Tự đóng do người nộp chưa xác nhận",
};

const OPERATION_LABEL: Record<string, string> = {
  add_to_group: "Thêm vào nhóm",
  remove_from_group: "Gỡ khỏi nhóm",
};

// Why a completion action was skipped, as the backend records it: "<operation> skipped: the record has no <what>".
function skippedReason(detail: string, fieldLabel: (name: string) => string): string {
  const match = /^(\S+) skipped: the record has no (?:user in field (\S+)|group in field (\S+)|group)$/.exec(detail);
  if (!match) return translateBackend(detail);
  const [, operation, userField, groupField] = match;
  const what = userField
    ? `chưa có người dùng ở trường "${fieldLabel(userField)}"`
    : groupField
      ? `chưa có nhóm ở trường "${fieldLabel(groupField)}"`
      : "chưa có nhóm";
  return `${OPERATION_LABEL[operation] ?? operation} bị bỏ qua: hồ sơ ${what}`;
}

// What happened to this record, newest first, as a timeline.
export function HistoryPanel({ model, entity }: Props) {
  const record = useRecordContext();
  const [events, setEvents] = useState<RecordEvent[]>([]);
  const id = record?.id;
  const version = record?.version;

  useEffect(() => {
    if (id !== undefined) listEvents(model, entity.name, id).then(setEvents, () => setEvents([]));
  }, [model, entity.name, id, version]);

  const process = entity.process;
  const actionLabel = (name: string) =>
    EVENT_LABELS[name] ?? process?.actions.find((action) => action.name === name)?.label ?? name;
  const fieldLabel = (name: string) => entity.fields.find((field) => field.name === name)?.label ?? name;
  const ordered = [...events].reverse();

  return (
    <Section title="Lịch sử xử lý">
      {ordered.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          Chưa có hoạt động nào.
        </Typography>
      )}
      <Box sx={{ position: "relative", pl: 3 }}>
        <Box sx={{ position: "absolute", left: 7, top: 6, bottom: 6, width: 2, bgcolor: "divider" }} />
        {ordered.map((event, index) => {
          const detail =
            event.note ??
            (event.action === "updated"
              ? event.detail?.split(", ").map(fieldLabel).join(", ")
              : event.action === "completion_skipped" && event.detail
                ? skippedReason(event.detail, fieldLabel)
                : null);
          const tone = event.to_status ? TONES[event.to_status] : undefined;
          const color = !tone || tone === "default" ? "text.disabled" : `${tone}.main`;
          return (
            <Box key={event.id} sx={{ position: "relative", pb: index === ordered.length - 1 ? 0 : 2.25 }}>
              <Box
                sx={{ position: "absolute", left: -24, top: 5, width: 12, height: 12, borderRadius: "50%", bgcolor: color, border: 2, borderColor: "background.paper" }}
              />
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                <Typography sx={{ fontWeight: 600 }}>{actionLabel(event.action)}</Typography>
                {event.to_status && event.to_status !== event.from_status && (
                  <StatusChip entity={entity} status={event.to_status} />
                )}
              </Box>
              <Typography variant="body2" color="text.secondary">
                {event.actor_name} · {timeAgo(event.at)}
                <Tooltip title={formatDateTime(event.at)}>
                  <span> · {formatDateTime(event.at)}</span>
                </Tooltip>
              </Typography>
              {(detail || (event.action === "assigned" && !event.detail)) && (
                <Typography variant="body2" sx={{ mt: 0.5, p: 1, bgcolor: "action.hover", borderRadius: 1 }}>
                  {event.action === "assigned" ? (event.detail ?? "Bỏ giao") : detail}
                </Typography>
              )}
            </Box>
          );
        })}
      </Box>
    </Section>
  );
}

// The files attached to this record, with a way to add one for each file field.
export function FilesPanel({ model, entity }: Props) {
  const record = useRecordContext();
  const { data: identity } = useGetIdentity();
  const notify = useNotify();
  const refresh = useRefresh();
  const [files, setFiles] = useState<AttachmentItem[]>([]);
  const [busy, setBusy] = useState(false);
  const id = record?.id;
  const version = record?.version;
  const fileFields = entity.fields.filter((field) => field.type === "file");

  const load = () => {
    if (id !== undefined) listFiles(model, entity.name, id).then(setFiles, () => setFiles([]));
  };
  useEffect(load, [model, entity.name, id, version]);

  if (fileFields.length === 0 || id === undefined) return null;
  const closed = entity.process?.states.find((state) => state.name === record?.status)?.terminal ?? false;
  const canAdd = entity.canWrite && !closed;

  const run = async (work: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await work();
      load();
      refresh();
    } catch (error) {
      notify(message(error), { type: "error" });
    } finally {
      setBusy(false);
    }
  };

  const open = (item: AttachmentItem) =>
    run(async () => {
      const link = await fileLink(model, entity.name, id, item.id);
      window.open(link.url, "_blank", "noopener");
    });

  return (
    <>
      {fileFields.map((field) => {
        const own = files.filter((item) => item.field === field.name);
        return (
          <Section
            key={field.name}
            title={field.label}
            action={
              canAdd ? (
                <Button component="label" size="small" variant="outlined" startIcon={<UploadFileIcon />} disabled={busy}>
                  Tải lên
                  <input
                    hidden
                    type="file"
                    onChange={(event) => {
                      const chosen = event.target.files?.[0];
                      event.target.value = "";
                      if (chosen) run(() => uploadFile(model, entity.name, id, field.name, chosen));
                    }}
                  />
                </Button>
              ) : undefined
            }
          >
            {own.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                Chưa có tệp nào.
              </Typography>
            )}
            {own.map((item) => (
              <Box key={item.id} sx={{ display: "flex", alignItems: "center", gap: 1, py: 0.75 }}>
                <AttachFileIcon fontSize="small" color="action" />
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Button size="small" disabled={busy} onClick={() => open(item)} sx={{ p: 0, minWidth: 0, textAlign: "left" }}>
                    <Typography noWrap sx={{ fontWeight: 600 }}>
                      {item.name}
                    </Typography>
                  </Button>
                  <Typography variant="caption" color="text.secondary" display="block">
                    {Math.max(1, Math.round(item.size / 1024))} KB
                  </Typography>
                </Box>
                {canAdd && item.uploaded_by === identity?.id && (
                  <Tooltip title="Gỡ tệp">
                    <IconButton size="small" color="error" disabled={busy} onClick={() => run(() => removeFile(model, entity.name, id, item.id))}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
            ))}
          </Section>
        );
      })}
    </>
  );
}
