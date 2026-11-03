// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import {
  getVersion,
  listApplied,
  listRuns,
  listVersions,
  planRollback,
  rollbackModel,
  type AppliedModel,
  type Plan,
  type Run,
} from "./api";
import PlanPanel from "./PlanPanel";

interface Props {
  refresh: number;
  onOpen: (model: string, content: string, version: number) => void;
  onChanged: (message: string) => void;
}

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");
const when = (value: string | null) => (value ? new Date(value).toLocaleString("vi-VN") : "");

// The models running now, with their history and the way back to the version before.
export default function ModelsTab({ refresh, onOpen, onChanged }: Props) {
  const [models, setModels] = useState<AppliedModel[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<string | null>(null);

  useEffect(() => {
    listApplied().then(setModels, (e: unknown) => setError(message(e)));
  }, [refresh]);

  const open = async (model: AppliedModel) => {
    try {
      const version = await getVersion(model.model, model.version);
      onOpen(model.model, version.content, model.version);
    } catch (e) {
      setError(message(e));
    }
  };

  return (
    <Box>
      {error && <Alert severity="error">{error}</Alert>}
      {models.length === 0 && !error && <Typography>Chưa có mô hình nào được áp dụng. Chọn một mẫu ở thư viện mẫu để bắt đầu.</Typography>}
      {models.length > 0 && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Mô hình</TableCell>
              <TableCell>Phiên bản</TableCell>
              <TableCell>Áp dụng bởi</TableCell>
              <TableCell>Lúc</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {models.map((model) => (
              <TableRow key={model.model}>
                <TableCell>{model.model}</TableCell>
                <TableCell>{model.version}</TableCell>
                <TableCell>{model.applied_by}</TableCell>
                <TableCell>{when(model.applied_at)}</TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => open(model)}>
                    Sửa
                  </Button>
                  <Button size="small" onClick={() => setHistory(model.model)}>
                    Lịch sử
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {history && <HistoryDialog model={history} onClose={() => setHistory(null)} onChanged={onChanged} />}
    </Box>
  );
}

function HistoryDialog({ model, onClose, onChanged }: { model: string; onClose: () => void; onChanged: (message: string) => void }) {
  const [versions, setVersions] = useState<{ version: number; applied_by: string; applied_at: string }[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [plan, setPlan] = useState<Plan | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listVersions(model).then(setVersions, (e: unknown) => setError(message(e)));
    listRuns(model).then(setRuns, (e: unknown) => setError(message(e)));
  }, [model]);

  const back = async () => {
    setBusy(true);
    try {
      setPlan(await planRollback(model));
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  const confirm = async (accept?: string) => {
    if (!plan) return;
    setBusy(true);
    try {
      const done = await rollbackModel(model, plan.fromVersion, accept);
      onChanged(`Đã quay về nội dung phiên bản trước, thành phiên bản ${done.version} của ${model}`);
      onClose();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Lịch sử của {model}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        <Typography variant="subtitle2">Phiên bản</Typography>
        <Table size="small">
          <TableBody>
            {versions.map((version) => (
              <TableRow key={version.version}>
                <TableCell>Phiên bản {version.version}</TableCell>
                <TableCell>{version.applied_by}</TableCell>
                <TableCell>{when(version.applied_at)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Typography variant="subtitle2" sx={{ mt: 2 }}>
          Các lần áp dụng
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Lần</TableCell>
              <TableCell>Kết quả</TableCell>
              <TableCell>Người chạy</TableCell>
              <TableCell>Bắt đầu</TableCell>
              <TableCell>Các bước</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {runs.map((run) => (
              <TableRow key={run.id}>
                <TableCell>{run.id}</TableCell>
                <TableCell>{run.status}</TableCell>
                <TableCell>{run.actor}</TableCell>
                <TableCell>{when(run.started_at)}</TableCell>
                <TableCell>{steps(run.steps)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {plan && <PlanPanel plan={plan} busy={busy} applyLabel="Quay về" onApply={confirm} />}
      </DialogContent>
      <DialogActions>
        {versions.length > 1 && !plan && (
          <Button disabled={busy} onClick={back}>
            Quay về phiên bản trước
          </Button>
        )}
        <Button onClick={onClose}>Đóng</Button>
      </DialogActions>
    </Dialog>
  );
}

function steps(raw: string): string {
  try {
    return (JSON.parse(raw) as { kind: string; status: string }[]).map((step) => `${step.kind}: ${step.status}`).join(", ");
  } catch {
    return "";
  }
}
