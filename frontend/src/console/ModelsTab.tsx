// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
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
import { t } from "../shell/t";

interface Props {
  refresh: number;
  onBrowse: () => void;
  onOpen: (model: string, content: string, version: number) => void;
  onChanged: (message: string) => void;
}

const message = (error: unknown) => (error instanceof Error ? error.message : t("Không xử lý được"));
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
        loaded.filter((model) => model.version > 0).forEach((model) =>
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
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fill, minmax(min(360px, 100%), 1fr))" }}>
        <Skeleton variant="rounded" height={170} />
        <Skeleton variant="rounded" height={170} />
      </Box>
    );
  }
  if (models.length === 0) {
    return (
      <Card sx={{ py: 8, textAlign: "center" }}>
        <LayersOutlinedIcon sx={{ fontSize: 48, color: "text.disabled", mb: 1 }} />
        <Typography variant="h6">{t("Chưa có mô hình nào được áp dụng")}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t("Chọn một mẫu ở thư viện mẫu để có ngay một hệ thống chạy được.")}
        </Typography>
        <Button variant="contained" onClick={onBrowse}>
          {t("Xem thư viện mẫu")}
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
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fill, minmax(min(360px, 100%), 1fr))" }}>
        {models.map((model) => {
          const summary = summaries[model.model];
          return (
            <Card key={model.model} sx={{ p: 2.5, display: "flex", flexDirection: "column", gap: 1.5 }}>
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
                <Typography variant="h6" noWrap>
                  {modelTitle(model.model)}
                </Typography>
                <Box sx={{ display: "flex", gap: 0.75 }}>
                  {model.held && <Chip size="small" color="warning" label={t("Đang dừng dở")} />}
                  <Chip size="small" color="primary" label={model.version > 0 ? t("Phiên bản {version}", { version: model.version }) : t("Chưa có phiên bản")} />
                </Box>
              </Box>
              {model.held && (
                <Alert severity="warning" sx={{ py: 0 }}>
                  {model.version > 0
                    ? t("Lần áp dụng gần nhất dừng giữa chừng nên người dùng chưa vào được mô hình này. Tiếp tục để hoàn tất, hoặc hủy bỏ để đưa các bảng về phiên bản {version}.", { version: model.version })
                    : t("Lần áp dụng đầu tiên dừng giữa chừng nên mô hình chưa dùng được. Tiếp tục để hoàn tất, hoặc hủy bỏ để xóa những gì lần đó đã tạo.")}
                </Alert>
              )}
              <Typography variant="body2" color="text.secondary">
                {model.version > 0 ? t("Áp dụng {time}", { time: timeAgo(model.applied_at) }) : t("Bắt đầu áp dụng {time}", { time: timeAgo(model.applied_at) })}
                <Tooltip title={t("{time} · người dùng {user}", { time: formatDateTime(model.applied_at), user: model.applied_by })}>
                  <span>{t(" · bởi {name}", { name: applier(model) })}</span>
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
                    {t("{entities} thực thể · {roles} vai · {units} đơn vị", { entities: summary.entities.length, roles: summary.roles, units: summary.units })}
                  </Typography>
                </>
              ) : model.version > 0 ? (
                <Skeleton height={48} />
              ) : null}
              <Box sx={{ display: "flex", gap: 1, mt: "auto", pt: 0.5, flexWrap: "wrap" }}>
                {model.held && (
                  <>
                    <Button
                      variant="contained"
                      color="warning"
                      disabled={working === model.model}
                      onClick={() => settle(model, resumeModel, t("Đã hoàn tất việc áp dụng {model}", { model: model.model }))}
                    >
                      {t("Tiếp tục áp dụng")}
                    </Button>
                    <Button color="error" disabled={working === model.model} onClick={() => setGiveUp(model)}>
                      {t("Hủy bỏ")}
                    </Button>
                  </>
                )}
                {model.version > 0 && (
                  <Button variant={model.held ? "outlined" : "contained"} onClick={() => open(model)}>
                    {t("Sửa mô hình")}
                  </Button>
                )}
                <Button startIcon={<HistoryIcon />} onClick={() => setHistory(model.model)}>
                  {t("Lịch sử")}
                </Button>
              </Box>
            </Card>
          );
        })}
      </Box>
      {giveUp && (
        <Dialog open onClose={() => setGiveUp(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{t("Hủy bỏ lần áp dụng dở của {model}?", { model: modelTitle(giveUp.model) })}</DialogTitle>
          <DialogContent>
            <Typography variant="body2">
              {giveUp.version > 0
                ? t("Các bảng được đưa về phiên bản {version}. Cột và bảng mà lần áp dụng dở đã thêm sẽ bị xóa cùng dữ liệu trong đó, còn những gì nó đã xóa thì không quay lại. Hãy sao lưu bằng deploy/backup.sh trước nếu cần.", { version: giveUp.version })
                : t("Lược đồ và các bảng mà lần áp dụng đầu tiên đã tạo sẽ bị xóa cùng dữ liệu trong đó.")}
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setGiveUp(null)}>{t("Giữ nguyên")}</Button>
            <Button
              color="error"
              variant="contained"
              disabled={working === giveUp.model}
              onClick={() => settle(giveUp, abandonModel, t("Đã hủy bỏ lần áp dụng dở của {model}", { model: giveUp.model }))}
            >
              {t("Hủy bỏ lần áp dụng")}
            </Button>
          </DialogActions>
        </Dialog>
      )}
      {history && <HistoryDialog model={history} hasVersion={(models.find((m) => m.model === history)?.version ?? 0) > 0} onClose={() => setHistory(null)} onChanged={onChanged} />}
    </Box>
  );
}

function HistoryDialog({
  model,
  hasVersion,
  onClose,
  onChanged,
}: {
  model: string;
  hasVersion: boolean;
  onClose: () => void;
  onChanged: (message: string) => void;
}) {
  const [versions, setVersions] = useState<AppliedVersion[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [plan, setPlan] = useState<Plan | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (hasVersion) listVersions(model).then(setVersions, (e: unknown) => setError(message(e)));
    listRuns(model).then(setRuns, (e: unknown) => setError(message(e)));
  }, [model, hasVersion]);

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
      onChanged(t("Đã quay về nội dung phiên bản trước, thành phiên bản {version} của {model}", { version: done.version, model }));
      onClose();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{t("Lịch sử của {model}", { model: modelTitle(model) })}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        <Typography variant="subtitle2" sx={{ mt: 1, mb: 0.5 }}>
          {t("Các phiên bản")}
        </Typography>
        <Table size="small">
          <TableBody>
            {versions.map((version, index) => (
              <TableRow key={version.version}>
                <TableCell>
                  {t("Phiên bản {version}", { version: version.version })}
                  {index === 0 && <Chip size="small" color="primary" label={t("Đang chạy")} sx={{ ml: 1 }} />}
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
          {t("Các lần áp dụng")}
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t("Lần")}</TableCell>
              <TableCell>{t("Kết quả")}</TableCell>
              <TableCell>{t("Bắt đầu")}</TableCell>
              <TableCell>{t("Các bước")}</TableCell>
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
        {plan && <PlanPanel plan={plan} busy={busy} applyLabel={t("Quay về")} onApply={confirm} />}
      </DialogContent>
      <DialogActions>
        {versions.length > 1 && !plan && (
          <Button disabled={busy} onClick={back}>
            {t("Quay về phiên bản trước")}
          </Button>
        )}
        <Button onClick={onClose}>{t("Đóng")}</Button>
      </DialogActions>
    </Dialog>
  );
}

function steps(raw: string): string {
  try {
    const list = JSON.parse(raw) as { kind: string; target: string; status: string }[];
    const failed = list.filter((step) => step.status !== "done");
    return failed.length === 0
      ? t("{n} bước đều xong", { n: list.length })
      : failed.map((step) => `${label(STEP_KIND, step.kind)} (${label(RUN_STATUS, step.status)})`).join(", ");
  } catch {
    return "";
  }
}
