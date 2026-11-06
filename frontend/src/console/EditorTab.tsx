// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
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
import YAML, { isSeq, type Document } from "yaml";
import { ApiError } from "../shell/api";
import { translateBackend } from "../shell/backendMessages";
import {
  applyModel,
  planModel,
  previewDiagram,
  type DiagramProblem,
  type Plan,
  type ProcessView,
} from "./api";
import ModelForm from "./ModelForm";
import PlanPanel from "./PlanPanel";
import { FAMILY_LABEL } from "./labels";
import ProcessDiagram from "./ProcessDiagram";
import YamlEditor from "./YamlEditor";

interface Props {
  text: string;
  source: string;
  schema: Record<string, unknown>;
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

type Path = (string | number)[];
const isMapping = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === "object" && !Array.isArray(value);

// Writes only what differs into the document, so the comments around what did not change stay in the file.
function patch(doc: Document, path: Path, before: unknown, after: unknown) {
  if (JSON.stringify(before) === JSON.stringify(after)) return;
  if (isMapping(before) && isMapping(after)) {
    Object.keys(before).forEach((key) => {
      if (!(key in after)) doc.deleteIn([...path, key]);
    });
    Object.entries(after).forEach(([key, value]) => patch(doc, [...path, key], before[key], value));
    return;
  }
  if (Array.isArray(before) && Array.isArray(after)) {
    const seq = doc.getIn(path, true);
    if (isSeq(seq)) {
      // Items that did not change keep their place and comments; only the stretch that differs is rewritten.
      const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
      let head = 0;
      while (head < before.length && head < after.length && same(before[head], after[head])) head++;
      let tail = 0;
      while (
        tail < before.length - head &&
        tail < after.length - head &&
        same(before[before.length - 1 - tail], after[after.length - 1 - tail])
      )
        tail++;
      const oldCount = before.length - head - tail;
      const newCount = after.length - head - tail;
      const common = Math.min(oldCount, newCount);
      for (let offset = 0; offset < common; offset++) {
        patch(doc, [...path, head + offset], before[head + offset], after[head + offset]);
      }
      // The comment above the first item hangs on the list, so it moves to whichever item is first afterwards.
      const lead = (item: unknown) => item as { commentBefore?: string | null } | undefined;
      if (newCount > common) {
        const added = after.slice(head + common, head + newCount).map((value) => doc.createNode(value));
        seq.items.splice(head + common, 0, ...added);
        const moved = lead(seq.items[head + common + added.length]);
        if (head + common === 0 && moved && seq.commentBefore) {
          moved.commentBefore = seq.commentBefore;
          seq.commentBefore = undefined;
        }
      } else if (oldCount > common) {
        seq.items.splice(head + common, oldCount - common);
        if (head + common === 0) {
          const first = lead(seq.items[0]);
          seq.commentBefore = first?.commentBefore ?? undefined;
          if (first) first.commentBefore = undefined;
        }
      }
      return;
    }
  }
  doc.setIn(path, after);
}

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");

// How long the file must stay untouched before it is checked and drawn again.
const DIAGRAM_DELAY_MS = 5000;

type DiagramState = "waiting" | "checking" | "ready" | "unavailable";

// The process diagrams of the file being written. The file is checked by the backend once it has
// stayed unchanged for DIAGRAM_DELAY_MS; a file with problems keeps the last diagrams that were fine.
function useDiagrams(text: string): { processes: ProcessView[]; problems: DiagramProblem[]; state: DiagramState } {
  const [processes, setProcesses] = useState<ProcessView[]>([]);
  const [problems, setProblems] = useState<DiagramProblem[]>([]);
  const [state, setState] = useState<DiagramState>("checking");
  const first = useRef(true);
  useEffect(() => {
    let current = true;
    setState(first.current ? "checking" : "waiting");
    const timer = window.setTimeout(
      () => {
        // An empty editor is a file still to be pasted, not a file with problems.
        if (!text.trim()) {
          setProblems([]);
          setState("ready");
          return;
        }
        setState("checking");
        previewDiagram(text)
          .then((result) => {
            if (!current) return;
            setProblems(result.errors);
            if (result.valid) setProcesses(result.processes);
            setState("ready");
          })
          .catch(() => current && setState("unavailable"));
      },
      first.current ? 0 : DIAGRAM_DELAY_MS,
    );
    first.current = false;
    return () => {
      current = false;
      window.clearTimeout(timer);
    };
  }, [text]);
  return { processes, problems, state };
}

function DiagramStatus({
  state,
  problems,
  drawn,
}: {
  state: DiagramState;
  problems: DiagramProblem[];
  drawn: boolean;
}) {
  if (problems.length > 0) {
    return (
      <Alert severity="warning" sx={{ mb: 1.5 }}>
        {drawn ? "Tệp có lỗi nên sơ đồ giữ bản gần nhất đúng:" : "Tệp có lỗi nên chưa vẽ được sơ đồ:"}
        {problems.map((problem) => (
          <div key={`${problem.line}:${problem.column}:${problem.message}`}>
            {problem.line > 0 ? `Dòng ${problem.line}, cột ${problem.column}: ` : ""}
            {translateBackend(problem.message)}
          </div>
        ))}
      </Alert>
    );
  }
  if (state === "unavailable") {
    return (
      <Alert severity="info" sx={{ mb: 1.5 }}>
        Chưa kiểm được tệp lúc này, sơ đồ giữ bản gần nhất.
      </Alert>
    );
  }
  if (state === "waiting" || state === "checking") {
    return (
      <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
        {state === "checking" && <CircularProgress size={12} />}
        {state === "waiting" ? "Sơ đồ cập nhật khi bạn ngừng gõ vài giây." : "Đang kiểm tệp…"}
      </Typography>
    );
  }
  return null;
}

// Writes a model with the form or in YAML, shows what applying it would do, and applies it.
export default function EditorTab({ text, source, schema, onText, onApplied }: Props) {
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

  const { processes, problems: diagramProblems, state: diagramState } = useDiagrams(text);
  const selected = processes.find((item) => item.entity === entity) ?? processes[0];

  // The form changes only the values it edits; every comment of the file is kept.
  const fromForm = (value: Record<string, unknown>) => {
    const next = prune(value) as Record<string, unknown>;
    if (JSON.stringify(next) === JSON.stringify(parsed.value)) return;
    const doc = YAML.parseDocument(text);
    patch(doc, [], parsed.value, next);
    onText(doc.toString({ lineWidth: 0 }));
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
                Điền form chỉ sửa những chỗ bạn đổi, các dòng chú thích trong tệp được giữ.
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
        {(processes.length > 0 || diagramProblems.length > 0) && (
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
            <DiagramStatus state={diagramState} problems={diagramProblems} drawn={processes.length > 0} />
            {processes.length > 0 && (
              <TextField select fullWidth size="small" sx={{ mb: 1.5 }} value={selected?.entity ?? ""} onChange={(event) => setEntity(event.target.value)}>
                {processes.map((item) => (
                  <MenuItem key={item.entity} value={item.entity}>
                    {item.entity} ({FAMILY_LABEL[item.family] ?? item.family})
                  </MenuItem>
                ))}
              </TextField>
            )}
            {selected && <ProcessDiagram family={selected} />}
          </Card>
        )}
        {selected && (
          <Dialog open={zoomed} onClose={() => setZoomed(false)} maxWidth="xl" fullWidth>
            <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Box sx={{ flexGrow: 1 }}>
                Sơ đồ quy trình: {selected?.entity} ({FAMILY_LABEL[selected.family] ?? selected.family})
              </Box>
              <IconButton onClick={() => setZoomed(false)} aria-label="Đóng">
                <CloseIcon />
              </IconButton>
            </DialogTitle>
            <DialogContent>
              <ProcessDiagram family={selected} height="68vh" spacing={360} />
            </DialogContent>
          </Dialog>
        )}
      </Box>
    </Box>
  );
}
