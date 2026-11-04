// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
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
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";
import HistoryIcon from "@mui/icons-material/History";
import LayersOutlinedIcon from "@mui/icons-material/LayersOutlined";
import { formatDateTime, timeAgo } from "../shell/format";
import {
  abandonModel,
  getVersion,
  listApplied,
  listRuns,
  listVersions,
  planRollback,
  resumeModel,
  rollbackModel,
  type AppliedModel,
  type AppliedVersion,
  type Plan,
  type Run,
} from "./api";
import { FAMILY_LABEL, RUN_STATUS, STEP_KIND, label, summarize, type ModelSummary } from "./labels";
import PlanPanel from "./PlanPanel";

interface Props {
  refresh: number;
  onBrowse: () => void;
  onOpen: (model: string, content: string, version: number) => void;
  onChanged: (message: string) => void;
}

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");
const modelTitle = (name: string) => name.replace(/_/g, " ").replace(/^./, (first) => first.toUpperCase());
const shortId = (id: string) => (id.length > 12 ? `${id.slice(0, 8)}…` : id);
// The name of who applied a version, or a short form of the user id for versions without one.
const applier = (version: { applied_by: string; applied_by_name: string | null }) =>
  version.applied_by_name || shortId(version.applied_by);

// The models running now, with their history and the way back to the version before.
export default function ModelsTab({ refresh, onBrowse, onOpen, onChanged }: Props) {
  const [models, setModels] = useState<AppliedModel[] | null>(null);
  const [summaries, setSummaries] = useState<Record<string, ModelSummary | null>>({});
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<string | null>(null);
  const [working, setWorking] = useState<string | null>(null);
  const [giveUp, setGiveUp] = useState<AppliedModel | null>(null);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    listApplied().then(
      (loaded) => {
        setModels(loaded);
        loaded.forEach((model) =>
          getVersion(model.model, model.version).then(
            (version) => setSummaries((current) => ({ ...current, [model.model]: summarize(version.content) })),
            () => undefined,
          ),
        );
      },
      (e: unknown) => setError(message(e)),
    );
  }, [refresh]);

  const open = async (model: AppliedModel) => {
    try {
      const version = await getVersion(model.model, model.version);
      onOpen(model.model, version.content, model.version);
    } catch (e) {
      setError(message(e));
    }
  };

  // Finishes or gives up the apply of a model that stopped before it finished.
  const settle = async (model: AppliedModel, action: typeof resumeModel, done: string) => {
    setWorking(model.model);
    setProblem(null);
    try {
      await action(model.model);
      setGiveUp(null);
      onChanged(done);
    } catch (e) {
      setGiveUp(null);
      setProblem(message(e));
    } finally {
      setWorking(null);
    }
  };

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!models) {
    return (
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fill, minmax(360px, 1fr))" }}>
        <Skeleton variant="rounded" height={170} />
        <Skeleton variant="rounded" height={170} />
      </Box>
    );
  }
  if (models.length === 0) {
    return (
      <Card sx={{ py: 8, textAlign: "center" }}>
        <LayersOutlinedIcon sx={{ fontSize: 48, color: "text.disabled", mb: 1 }} />
        <Typography variant="h6">Chưa có mô hình nào được áp dụng</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Chọn một mẫu ở thư viện mẫu để có ngay một hệ thống chạy được.
        </Typography>
        <Button variant="contained" onClick={onBrowse}>
          Xem thư viện mẫu
        </Button>
      </Card>
    );
  }

  return (
    <Box>
      {problem && (
        <Alert severity="error" onClose={() => setProblem(null)} sx={{ mb: 2 }}>
          {problem}
        </Alert>
      )}
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fill, minmax(360px, 1fr))" }}>
        {models.map((model) => {
          const summary = summaries[model.model];
          return (
            <Card key={model.model} sx={{ p: 2.5, display: "flex", flexDirection: "column", gap: 1.5 }}>
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
                <Typography variant="h6" noWrap>
                  {modelTitle(model.model)}
                </Typography>
                <Box sx={{ display: "flex", gap: 0.75 }}>
                  {model.held && <Chip size="small" color="warning" label="Đang dừng dở" />}
                  <Chip size="small" color="primary" label={`Phiên bản ${model.version}`} />
                </Box>
              </Box>
              {model.held && (
                <Alert severity="warning" sx={{ py: 0 }}>
                  Lần áp dụng gần nhất dừng giữa chừng nên người dùng chưa vào được mô hình này. Tiếp tục để hoàn tất, hoặc hủy bỏ để đưa các bảng về phiên bản {model.version}.
                </Alert>
              )}
              <Typography variant="body2" color="text.secondary">
                Áp dụng {timeAgo(model.applied_at)}
                <Tooltip title={`${formatDateTime(model.applied_at)} · người dùng ${model.applied_by}`}>
                  <span> · bởi {applier(model)}</span>
                </Tooltip>
              </Typography>
              {summary ? (
                <>
                  <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap" }}>
                    {summary.entities.map((entity) => (
                      <Tooltip key={entity.name} title={FAMILY_LABEL[entity.family] ?? entity.family}>
                        <Chip size="small" variant="outlined" label={entity.label} />
                      </Tooltip>
                    ))}
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    {summary.entities.length} thực thể · {summary.roles} vai · {summary.units} đơn vị
                  </Typography>
                </>
              ) : (
                <Skeleton height={48} />
              )}
              <Box sx={{ display: "flex", gap: 1, mt: "auto", pt: 0.5, flexWrap: "wrap" }}>
                {model.held && (
                  <>
                    <Button
                      variant="contained"
                      color="warning"
                      disabled={working === model.model}
                      onClick={() => settle(model, resumeModel, `Đã hoàn tất việc áp dụng ${model.model}`)}
                    >
                      Tiếp tục áp dụng
                    </Button>
                    <Button color="error" disabled={working === model.model} onClick={() => setGiveUp(model)}>
                      Hủy bỏ
                    </Button>
                  </>
                )}
                <Button variant={model.held ? "outlined" : "contained"} onClick={() => open(model)}>
                  Sửa mô hình
                </Button>
                <Button startIcon={<HistoryIcon />} onClick={() => setHistory(model.model)}>
                  Lịch sử
                </Button>
              </Box>
            </Card>
          );
        })}
      </Box>
      {giveUp && (
        <Dialog open onClose={() => setGiveUp(null)} maxWidth="sm" fullWidth>
          <DialogTitle>Hủy bỏ lần áp dụng dở của {modelTitle(giveUp.model)}?</DialogTitle>
          <DialogContent>
            <Typography variant="body2">
              Các bảng được đưa về phiên bản {giveUp.version}. Cột và bảng mà lần áp dụng dở đã thêm sẽ bị xóa cùng dữ liệu trong đó, còn những gì nó đã xóa thì không quay lại. Hãy sao lưu bằng deploy/backup.sh trước nếu cần.
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setGiveUp(null)}>Giữ nguyên</Button>
            <Button
              color="error"
              variant="contained"
              disabled={working === giveUp.model}
              onClick={() => settle(giveUp, abandonModel, `Đã hủy bỏ lần áp dụng dở của ${giveUp.model}`)}
            >
              Hủy bỏ lần áp dụng
            </Button>
          </DialogActions>
        </Dialog>
      )}
      {history && <HistoryDialog model={history} onClose={() => setHistory(null)} onChanged={onChanged} />}
    </Box>
  );
}

function HistoryDialog({ model, onClose, onChanged }: { model: string; onClose: () => void; onChanged: (message: string) => void }) {
  const [versions, setVersions] = useState<AppliedVersion[]>([]);
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
      <DialogTitle>Lịch sử của {modelTitle(model)}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        <Typography variant="subtitle2" sx={{ mt: 1, mb: 0.5 }}>
          Các phiên bản
        </Typography>
        <Table size="small">
          <TableBody>
            {versions.map((version, index) => (
              <TableRow key={version.version}>
                <TableCell>
                  Phiên bản {version.version}
                  {index === 0 && <Chip size="small" color="primary" label="Đang chạy" sx={{ ml: 1 }} />}
                </TableCell>
                <TableCell>{formatDateTime(version.applied_at)}</TableCell>
                <TableCell>
                  <Tooltip title={version.applied_by}>
                    <span>{applier(version)}</span>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Typography variant="subtitle2" sx={{ mt: 2.5, mb: 0.5 }}>
          Các lần áp dụng
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Lần</TableCell>
              <TableCell>Kết quả</TableCell>
              <TableCell>Bắt đầu</TableCell>
              <TableCell>Các bước</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {runs.map((run) => (
              <TableRow key={run.id}>
                <TableCell>{run.id}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    variant="outlined"
                    color={run.status === "done" ? "success" : run.status === "failed" ? "error" : "default"}
                    label={label(RUN_STATUS, run.status)}
                  />
                </TableCell>
                <TableCell>{formatDateTime(run.started_at)}</TableCell>
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
    const list = JSON.parse(raw) as { kind: string; target: string; status: string }[];
    const failed = list.filter((step) => step.status !== "done");
    return failed.length === 0
      ? `${list.length} bước đều xong`
      : failed.map((step) => `${label(STEP_KIND, step.kind)} (${label(RUN_STATUS, step.status)})`).join(", ");
  } catch {
    return "";
  }
}
