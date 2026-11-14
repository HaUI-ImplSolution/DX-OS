// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  TextField as MuiTextField,
  Tooltip,
  Typography,
  type ButtonProps,
} from "@mui/material";
import { useGetIdentity, useListContext, useRefresh, useUnselectAll } from "react-admin";
import { takeAction } from "./api";
import { TONES } from "./StatusChip";
import type { EntityMeta, ProcessAction } from "./types";
import { t, ta } from "../shell/t";

// A batch is capped and sent a few at a time, so it stays inside the rate limit of the gateway.
const MAX_BATCH = 50;
const PARALLEL = 3;

interface Outcome {
  id: number;
  error: string | null;
}

interface Props {
  model: string;
  entity: EntityMeta;
}

// Takes one action on every selected record through the single-record call, so each goes
// through its own rules; a refused record is reported with its reason and the others go on.
export default function BulkActions({ model, entity }: Props) {
  const { selectedIds, data, resource } = useListContext();
  const { data: identity } = useGetIdentity();
  const unselectAll = useUnselectAll(resource);
  const refresh = useRefresh();
  const [pending, setPending] = useState<ProcessAction | null>(null);
  const [note, setNote] = useState("");
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [outcomes, setOutcomes] = useState<Outcome[] | null>(null);
  const process = entity.process;
  if (!process) return null;

  const me = identity?.id;
  const selected = (data ?? []).filter((record) => selectedIds.includes(record.id));
  const tooMany = selectedIds.length > MAX_BATCH;

  // An action is offered when it fits every selected record this page shows.
  const offered = process.actions.filter(
    (action) =>
      action.by === "HANDLER" &&
      entity.handlerActions?.includes(action.name) === true &&
      selected.every(
        (record) =>
          action.from.includes(record.status) &&
          (process.allowSelfDecision || record.created_by !== me) &&
          (!record.assignee || record.assignee === me),
      ),
  );

  const run = async (action: ProcessAction, text: string | undefined) => {
    const ids = [...selectedIds] as number[];
    const results: Outcome[] = [];
    let next = 0;
    let done = 0;
    setProgress({ done, total: ids.length });
    const worker = async () => {
      while (next < ids.length) {
        const index = next++;
        const id = ids[index];
        try {
          await takeAction(model, entity.name, id, action.name, text);
          results[index] = { id, error: null };
        } catch (error) {
          results[index] = { id, error: error instanceof Error ? error.message : t("Không xử lý được") };
        }
        done += 1;
        setProgress({ done, total: ids.length });
      }
    };
    await Promise.all(Array.from({ length: Math.min(PARALLEL, ids.length) }, worker));
    setProgress(null);
    setPending(null);
    setNote("");
    setOutcomes(results);
    unselectAll();
    refresh();
  };

  const failed = outcomes?.filter((outcome) => outcome.error !== null) ?? [];
  const style = (action: ProcessAction) => {
    const tone = TONES[action.to];
    const color: ButtonProps["color"] = tone === "success" || tone === "error" || tone === "warning" ? tone : "primary";
    return { variant: tone === "success" ? ("contained" as const) : ("outlined" as const), color };
  };

  return (
    <>
      {offered.map((action) => {
        const button = (
          <Button {...style(action)} size="small" disabled={tooMany} sx={{ ml: 1 }} onClick={() => setPending(action)}>
            {ta(action.label)}
          </Button>
        );
        return tooMany ? (
          <Tooltip key={action.name} title={t("Tối đa {n} hồ sơ mỗi lần", { n: MAX_BATCH })}>
            <span>{button}</span>
          </Tooltip>
        ) : (
          <span key={action.name}>{button}</span>
        );
      })}
      <Dialog open={pending !== null} onClose={() => !progress && setPending(null)} fullWidth>
        <DialogTitle>{pending ? t("{action}: {n} hồ sơ", { action: ta(pending.label), n: selectedIds.length }) : null}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            {t("Mỗi hồ sơ vẫn qua đủ luật của quy trình; hồ sơ không thực hiện được sẽ báo lý do, các hồ sơ khác vẫn được xử lý.")}
          </DialogContentText>
          {pending && (
            <MuiTextField
              fullWidth
              multiline
              minRows={2}
              label={pending.needsNote ? t("Ghi chú chung") : t("Lý do chung (không bắt buộc)")}
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          )}
          {progress && <LinearProgress variant="determinate" value={(progress.done / progress.total) * 100} sx={{ mt: 2 }} />}
        </DialogContent>
        <DialogActions>
          <Button disabled={progress !== null} onClick={() => setPending(null)}>
            {t("Hủy")}
          </Button>
          <Button
            disabled={progress !== null || (pending?.needsNote === true && !note.trim())}
            onClick={() => pending && run(pending, note.trim() || undefined)}
          >
            {progress ? t("Đang xử lý {done}/{total}", { done: progress.done, total: progress.total }) : t("Thực hiện")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={outcomes !== null} onClose={() => setOutcomes(null)} fullWidth>
        <DialogTitle>{t("Kết quả")}</DialogTitle>
        <DialogContent>
          <Alert severity={failed.length === 0 ? "success" : "warning"} sx={{ mb: 1 }}>
            {t("{ok} hồ sơ xong, {failed} hồ sơ không thực hiện được", { ok: (outcomes?.length ?? 0) - failed.length, failed: failed.length })}
          </Alert>
          <List dense>
            {failed.map((outcome) => (
              <ListItem key={outcome.id} disableGutters>
                <ListItemText primary={`#${outcome.id}`} secondary={outcome.error} />
              </ListItem>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOutcomes(null)}>{t("Đóng")}</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

// The bar over the table while rows are selected. The theme clips what slides out above the table,
// so the bar is drawn inside it instead of using the sliding bar of react-admin.
export function BulkBar({ model, entity }: Props) {
  const { selectedIds, onUnselectItems } = useListContext();
  // The bar stays mounted when nothing is selected, so the dialogs of a finished batch are not lost.
  return (
    <Box
      sx={{
        display: selectedIds.length === 0 ? "none" : "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: 0.5,
        px: 2,
        py: 1,
        bgcolor: "action.selected",
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {t("Đã chọn {n} mục", { n: selectedIds.length })}
      </Typography>
      <BulkActions model={model} entity={entity} />
      <Button size="small" sx={{ ml: "auto" }} onClick={() => onUnselectItems()}>
        {t("Bỏ chọn")}
      </Button>
    </Box>
  );
}
