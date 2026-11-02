// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField as MuiTextField,
  Typography,
} from "@mui/material";
import { useGetIdentity, useNotify, useRecordContext, useRefresh } from "react-admin";
import { assignRecord, listEvents, listHandlers, takeAction } from "./api";
import type { EntityMeta, Person, ProcessAction, RecordEvent } from "./types";

interface Props {
  model: string;
  entity: EntityMeta;
}

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");

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
    if (action.from !== record.status) return false;
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
          variant="outlined"
          sx={{ ml: 1 }}
          disabled={busy}
          onClick={() => (action.needsNote ? setNoteFor(action) : send(action))}
        >
          {action.label}
        </Button>
      ))}
      <Dialog open={noteFor !== null} onClose={() => setNoteFor(null)} fullWidth>
        <DialogTitle>{noteFor?.label}</DialogTitle>
        <DialogContent>
          <MuiTextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label="Ghi chú"
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNoteFor(null)}>Hủy</Button>
          <Button disabled={busy || !note.trim()} onClick={() => noteFor && send(noteFor, note)}>
            Gửi
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
    <Box sx={{ mt: 1 }}>
      <Typography variant="body2" sx={{ mb: 1 }}>
        Người xử lý: {record.assignee ? (holder ?? "Đã giao") : "Chưa giao"}
      </Typography>
      <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
        {canClaim && (
          <Button variant="outlined" onClick={() => assign(String(me))}>
            Nhận việc
          </Button>
        )}
        <MuiTextField
          select
          size="small"
          sx={{ minWidth: 220 }}
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
    </Box>
  );
}

const EVENT_LABELS: Record<string, string> = {
  created: "Tạo",
  updated: "Sửa",
  deleted: "Xóa",
  assigned: "Giao việc",
};

// What happened to this record, oldest first.
export function HistoryPanel({ model, entity }: Props) {
  const record = useRecordContext();
  const [events, setEvents] = useState<RecordEvent[]>([]);
  const id = record?.id;
  const version = record?.version;

  useEffect(() => {
    if (id !== undefined) listEvents(model, entity.name, id).then(setEvents, () => setEvents([]));
  }, [model, entity.name, id, version]);

  const process = entity.process;
  const stateLabel = (name: string | null) =>
    process?.states.find((state) => state.name === name)?.label ?? name ?? "";
  const actionLabel = (name: string) =>
    EVENT_LABELS[name] ?? process?.actions.find((action) => action.name === name)?.label ?? name;
  const fieldLabel = (name: string) => entity.fields.find((field) => field.name === name)?.label ?? name;

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle2">Lịch sử</Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Thời điểm</TableCell>
            <TableCell>Người thực hiện</TableCell>
            <TableCell>Việc</TableCell>
            <TableCell>Trạng thái</TableCell>
            <TableCell>Ghi chú</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {events.map((event) => (
            <TableRow key={event.id}>
              <TableCell>{new Date(event.at).toLocaleString("vi-VN")}</TableCell>
              <TableCell>{event.actor_name}</TableCell>
              <TableCell>{actionLabel(event.action)}</TableCell>
              <TableCell>{stateLabel(event.to_status)}</TableCell>
              <TableCell>
                {event.note ??
                  (event.action === "updated" ? event.detail?.split(", ").map(fieldLabel).join(", ") : "")}
                {event.action === "assigned" && (event.detail ?? "Bỏ giao")}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
