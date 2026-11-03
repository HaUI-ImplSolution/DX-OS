// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from "react";
import { Alert, Box, Button, MenuItem, TextField, ToggleButton, ToggleButtonGroup, Typography } from "@mui/material";
import YAML from "yaml";
import { ApiError } from "../shell/api";
import { applyModel, planModel, type Family, type Plan } from "./api";
import ModelForm from "./ModelForm";
import PlanPanel from "./PlanPanel";
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

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 1 }}>
        <ToggleButtonGroup size="small" exclusive value={mode} onChange={(_, next) => next && setMode(next)}>
          <ToggleButton value="yaml">Soạn YAML</ToggleButton>
          <ToggleButton value="form">Điền form</ToggleButton>
        </ToggleButtonGroup>
        <Typography variant="body2" sx={{ flexGrow: 1 }}>
          {source}
        </Typography>
        <Button variant="contained" disabled={busy || parsed.error !== null} onClick={showPlan}>
          Xem kế hoạch
        </Button>
      </Box>
      {problems.length > 0 && (
        <Alert severity="error" sx={{ mb: 1 }}>
          {problems.map((problem) => (
            <div key={problem}>{problem}</div>
          ))}
        </Alert>
      )}
      {mode === "yaml" && <YamlEditor value={text} onChange={onText} />}
      {mode === "form" &&
        (parsed.error ? (
          <Alert severity="warning">Sửa lỗi cú pháp ở chế độ YAML trước: {parsed.error}</Alert>
        ) : (
          <>
            <Alert severity="info" sx={{ mb: 1 }}>
              Điền form viết lại tệp, dòng chú thích đầu tệp được giữ.
            </Alert>
            <ModelForm schema={schema} value={parsed.value} onChange={fromForm} />
          </>
        ))}
      {plan && <PlanPanel plan={plan} busy={busy} onApply={apply} />}
      {processes.length > 0 && (
        <Box sx={{ mt: 3 }}>
          <Box sx={{ display: "flex", gap: 2, alignItems: "center", mb: 1 }}>
            <Typography variant="h6">Sơ đồ quy trình</Typography>
            <TextField select size="small" value={selected?.name ?? ""} onChange={(event) => setEntity(event.target.value)}>
              {processes.map((item) => (
                <MenuItem key={item.name} value={item.name}>
                  {item.name} ({item.family})
                </MenuItem>
              ))}
            </TextField>
          </Box>
          {family && <ProcessDiagram family={family} />}
        </Box>
      )}
    </Box>
  );
}
