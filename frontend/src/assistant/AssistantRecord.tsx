// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { request } from "../shell/api";
import { formatDateTime } from "../shell/format";
import { StatusChip } from "../admin/StatusChip";
import type { EntityMeta, ModelMeta } from "../admin/types";
import AssistantResult, { type RecordOperation } from "./AssistantResult";
import useAssistantQuery from "./useAssistantQuery";

export default function AssistantRecord({
  model,
  entity,
  recordId,
  initialOperation,
  autoRun = true,
  onClose,
}: {
  model: ModelMeta;
  entity: EntityMeta;
  recordId: number;
  initialOperation: RecordOperation;
  autoRun?: boolean;
  onClose: () => void;
}) {
  const [operation, setOperation] = useState(initialOperation);
  const [record, setRecord] = useState<Record<string, unknown> | null>(null);
  const [loadError, setLoadError] = useState("");
  const [validation, setValidation] = useState("");
  const [query, setQuery] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const context = `${model.name}/${entity.name}/${recordId}`;
  const task = useAssistantQuery(JSON.stringify([context, operation, query, from, to]));

  useEffect(() => {
    const controller = new AbortController();
    setRecord(null);
    setLoadError("");
    request<Record<string, unknown>>(`/data/${context}`, { signal: controller.signal }).then(
      (value) => {
        if (controller.signal.aborted) return;
        setRecord(value);
        if (autoRun) void task.run({ model: model.name, entity: entity.name, recordId, operation });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setLoadError(error instanceof Error ? error.message : "Không đọc được hồ sơ.");
      },
    );
    return () => controller.abort();
  }, [context, model.name, entity.name, recordId, operation, autoRun, task.run]);

  const texts = entity.fields.filter((field) => field.type === "text");
  const titleField = ["name", "title", "code"].map((name) => texts.find((field) => field.name === name)).find(Boolean) ?? texts[0];
  const title = record ? String(record[titleField?.name ?? "id"] ?? entity.label) : entity.label;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setValidation("");
    if (from && to && new Date(from) > new Date(to)) {
      setValidation("Thời điểm bắt đầu lịch sử phải trước thời điểm kết thúc.");
      return;
    }
    void task.run({
      model: model.name,
      entity: entity.name,
      recordId,
      operation,
      query,
      historyWindow: from || to ? { from: from ? new Date(from).toISOString() : null, to: to ? new Date(to).toISOString() : null } : null,
    });
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="md"
      aria-labelledby="assistant-record-heading"
      PaperProps={{ sx: { m: { xs: 1, sm: 4 }, width: { xs: "calc(100% - 16px)", sm: "100%" }, minWidth: 0 } }}
    >
      <DialogTitle id="assistant-record-heading" sx={{ overflowWrap: "anywhere" }}>
        {title}
      </DialogTitle>
      <DialogContent
        dividers
        onClick={(event) => {
          if ((event.target as Element).closest("a[href]")) onClose();
        }}
        sx={{ minWidth: 0, contain: "inline-size" }}
      >
        <Stack spacing={2}>
          {loadError ? (
            <Alert severity="error">{loadError}</Alert>
          ) : !record ? (
            <Box role="status">
              <CircularProgress size={20} /> Đang đọc hồ sơ…
            </Box>
          ) : (
            <>
              <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, alignItems: "center" }}>
                {entity.hasProcess && <StatusChip entity={entity} status={String(record.status ?? "")} />}
                <Typography variant="body2" color="text.secondary">
                  Dữ liệu từ hồ sơ
                </Typography>
                {typeof record.handle_by === "string" && (
                  <Typography variant="body2">Hạn xử lý: {formatDateTime(record.handle_by)}</Typography>
                )}
              </Box>
              <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                {(["summarize", "similar"] as const).map((value) => (
                  <Button
                    key={value}
                    variant={operation === value ? "contained" : "outlined"}
                    onClick={() => {
                      if (value === operation) return;
                      setQuery("");
                      setFrom("");
                      setTo("");
                      setValidation("");
                      setOperation(value);
                    }}
                  >
                    {value === "summarize" ? "Tóm tắt diễn biến" : "Tìm trường hợp tương tự"}
                  </Button>
                ))}
              </Box>
              <Box component="form" onSubmit={submit}>
                <Stack spacing={2}>
                  <TextField
                    fullWidth
                    label="Điều bạn muốn tìm hiểu (tùy chọn)"
                    multiline
                    minRows={2}
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    inputProps={{ maxLength: 1000 }}
                  />
                  <Accordion disableGutters>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Typography>Tùy chọn nâng cao</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={2}>
                        <Typography variant="body2" color="text.secondary">
                          Chọn khoảng sự kiện muốn đọc; không lọc ngày tạo hồ sơ.
                        </Typography>
                        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                          <TextField
                            fullWidth
                            label="Lịch sử từ"
                            type="datetime-local"
                            value={from}
                            InputLabelProps={{ shrink: true }}
                            onChange={(event) => setFrom(event.target.value)}
                          />
                          <TextField
                            fullWidth
                            label="Lịch sử đến"
                            type="datetime-local"
                            value={to}
                            InputLabelProps={{ shrink: true }}
                            onChange={(event) => setTo(event.target.value)}
                          />
                        </Stack>
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                  {(task.error || validation) && <Alert severity="error">{task.error || validation}</Alert>}
                  <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, alignItems: "center" }}>
                    <Button type="submit" variant="outlined" disabled={task.busy}>
                      Tra cứu
                    </Button>
                    {task.busy && (
                      <>
                        <CircularProgress size={20} aria-label="Đang xử lý" />
                        <Button onClick={task.cancel}>Hủy chờ</Button>
                      </>
                    )}
                  </Box>
                </Stack>
              </Box>
              {task.result && <AssistantResult result={task.result} model={model} entity={entity} />}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Đóng</Button>
      </DialogActions>
    </Dialog>
  );
}
