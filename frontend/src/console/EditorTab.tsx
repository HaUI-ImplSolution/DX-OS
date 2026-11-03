// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Step,
  StepLabel,
  Stepper,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import OpenInFullIcon from "@mui/icons-material/OpenInFull";
import YAML from "yaml";
import { ApiError } from "../shell/api";
import { applyModel, planModel, type Family, type Plan } from "./api";
import ModelForm from "./ModelForm";
import PlanPanel from "./PlanPanel";
import { FAMILY_LABEL } from "./labels";
import ProcessDiagram from "./ProcessDiagram";
import YamlEditor from "./YamlEditor";

interface Props {
  text: string;
  source: string;
  schema: Record<string, unknown>;
  families: Family[];
  onText: (text: string) => void;
  onApplied: (message: string) => void;
}

// The form leaves empty strings, lists and objects behind for fields nobody filled in.
function prune(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(prune);
  if (value === null || typeof value !== "object") return value;
  const kept: Record<string, unknown> = {};
  Object.entries(value).forEach(([key, item]) => {
    const cleaned = prune(item);
    const empty =
      cleaned === undefined ||
      cleaned === "" ||
      (Array.isArray(cleaned) && cleaned.length === 0) ||
      (typeof cleaned === "object" && cleaned !== null && !Array.isArray(cleaned) && Object.keys(cleaned).length === 0);
    if (!empty) kept[key] = cleaned;
  });
  return kept;
}

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");

// Writes a model with the form or in YAML, shows what applying it would do, and applies it.
export default function EditorTab({ text, source, schema, families, onText, onApplied }: Props) {
  const [mode, setMode] = useState<"form" | "yaml">("yaml");
  const [plan, setPlan] = useState<Plan | null>(null);
  const [problems, setProblems] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [entity, setEntity] = useState("");
  const [zoomed, setZoomed] = useState(false);

  useEffect(() => {
    setPlan(null);
    setProblems([]);
  }, [text]);

  const parsed = useMemo(() => {
    try {
      const value = YAML.parse(text);
      return { value: (value ?? {}) as Record<string, unknown>, error: null as string | null };
    } catch (error) {
      return { value: {} as Record<string, unknown>, error: message(error) };
    }
  }, [text]);

  const processes = useMemo(() => {
    const entities = (parsed.value.entities ?? {}) as Record<string, { process?: { family?: string } }>;
    return Object.entries(entities).filter(([, item]) => item?.process?.family).map(([name, item]) => ({ name, family: item.process?.family as string }));
  }, [parsed]);
  const selected = processes.find((item) => item.name === entity) ?? processes[0];
  const family = families.find((item) => item.name === selected?.family);

  // The form writes the file again; the comment lines at the top are kept.
  const fromForm = (value: Record<string, unknown>) => {
    const next = prune(value) as Record<string, unknown>;
    if (JSON.stringify(next) === JSON.stringify(parsed.value)) return;
    const header = text.match(/^(?:#.*\n)+/)?.[0] ?? "";
    onText(header + YAML.stringify(next, { lineWidth: 0 }));
  };

  const run = async (work: () => Promise<void>) => {
    setBusy(true);
    setProblems([]);
    try {
      await work();
    } catch (error) {
      setPlan(null);
      setProblems(error instanceof ApiError && Array.isArray(error.data) ? (error.data as string[]) : [message(error)]);
    } finally {
      setBusy(false);
    }
  };

  const showPlan = () => run(async () => setPlan(await planModel(text)));
  const apply = (accept?: string) =>
    run(async () => {
      if (!plan) return;
      const done = await applyModel(text, plan.fromVersion > 0 ? plan.fromVersion : undefined, accept);
      setPlan(null);
      onApplied(`Đã áp dụng ${done.model} phiên bản ${done.version}`);
    });

  const step = plan ? 2 : 1;
  return (
    <Box sx={{ display: "grid", gap: 2, alignItems: "start", gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 3fr) minmax(0, 2fr)" } }}>
      <Card sx={{ p: 2 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1.5, flexWrap: "wrap" }}>
          <ToggleButtonGroup size="small" exclusive value={mode} onChange={(_, next) => next && setMode(next)}>
            <ToggleButton value="yaml">Soạn YAML</ToggleButton>
            <ToggleButton value="form">Điền form</ToggleButton>
          </ToggleButtonGroup>
          <Chip size="small" variant="outlined" label={source} />
          <Box sx={{ flexGrow: 1 }} />
          <Button variant="contained" disabled={busy || parsed.error !== null} onClick={showPlan}>
            Xem kế hoạch
          </Button>
        </Box>
        {problems.length > 0 && (
          <Alert severity="error" sx={{ mb: 1.5 }}>
            {problems.map((problem) => (
              <div key={problem}>{problem}</div>
            ))}
          </Alert>
        )}
        {mode === "yaml" && <YamlEditor value={text} onChange={onText} height="min(62vh, 640px)" />}
        {mode === "form" &&
          (parsed.error ? (
            <Alert severity="warning">Sửa lỗi cú pháp ở chế độ YAML trước: {parsed.error}</Alert>
          ) : (
            <>
              <Alert severity="info" sx={{ mb: 1.5 }}>
                Điền form viết lại tệp, dòng chú thích đầu tệp được giữ.
              </Alert>
              <ModelForm schema={schema} value={parsed.value} onChange={fromForm} />
            </>
          ))}
      </Card>
      <Box sx={{ display: "grid", gap: 2 }}>
        <Card sx={{ p: 2.5 }}>
          <Stepper activeStep={step} alternativeLabel sx={{ mb: plan ? 2.5 : 0 }}>
            <Step>
              <StepLabel>Soạn mô hình</StepLabel>
            </Step>
            <Step>
              <StepLabel>Xem kế hoạch</StepLabel>
            </Step>
            <Step>
              <StepLabel>Áp dụng</StepLabel>
            </Step>
          </Stepper>
          {!plan && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
              Soạn xong thì bấm "Xem kế hoạch": hệ thống so tệp với mô hình đang chạy, cho biết sẽ đổi gì, mức rủi ro và
              những dòng dữ liệu vi phạm quy tắc mới, mà chưa thay đổi gì.
            </Typography>
          )}
          {plan && <PlanPanel plan={plan} busy={busy} onApply={apply} />}
        </Card>
        {processes.length > 0 && (
          <Card sx={{ p: 2.5 }}>
            <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
              <Typography variant="h6" sx={{ flexGrow: 1 }}>
                Sơ đồ quy trình
              </Typography>
              <Tooltip title="Mở to">
                <IconButton size="small" onClick={() => setZoomed(true)} aria-label="Mở to sơ đồ">
                  <OpenInFullIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>
            <TextField select fullWidth size="small" sx={{ mb: 1.5 }} value={selected?.name ?? ""} onChange={(event) => setEntity(event.target.value)}>
              {processes.map((item) => (
                <MenuItem key={item.name} value={item.name}>
                  {item.name} ({FAMILY_LABEL[item.family] ?? item.family})
                </MenuItem>
              ))}
            </TextField>
            {family && <ProcessDiagram family={family} />}
          </Card>
        )}
        {family && (
          <Dialog open={zoomed} onClose={() => setZoomed(false)} maxWidth="xl" fullWidth>
            <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Box sx={{ flexGrow: 1 }}>
                Sơ đồ quy trình: {selected?.name} ({FAMILY_LABEL[family.name] ?? family.name})
              </Box>
              <IconButton onClick={() => setZoomed(false)} aria-label="Đóng">
                <CloseIcon />
              </IconButton>
            </DialogTitle>
            <DialogContent>
              <ProcessDiagram family={family} height="68vh" spacing={360} />
            </DialogContent>
          </Dialog>
        )}
      </Box>
    </Box>
  );
}
