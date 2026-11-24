// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from "react";
import { Alert, CircularProgress, Typography } from "@mui/material";
import { previewDiagram, type DiagramProblem, type ProcessView } from "./api";
import { translateBackend } from "../shell/backendMessages";
import { t } from "../shell/t";

// How long the file must stay untouched before it is checked and drawn again.
const DIAGRAM_DELAY_MS = 5000;

export type DiagramState = "waiting" | "checking" | "ready" | "unavailable";

export interface Diagrams {
  processes: ProcessView[];
  problems: DiagramProblem[];
  state: DiagramState;
}

// The process diagrams of the file being written. The file is checked by the backend once it has
// stayed unchanged for DIAGRAM_DELAY_MS; a file with problems keeps the last diagrams that were fine.
export function useDiagrams(text: string): Diagrams {
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

export function DiagramStatus({
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
        {drawn ? t("Tệp có lỗi nên sơ đồ giữ bản gần nhất đúng:") : t("Tệp có lỗi nên chưa vẽ được sơ đồ:")}
        {problems.map((problem) => (
          <div key={`${problem.line}:${problem.column}:${problem.message}`}>
            {problem.line > 0 ? t("Dòng {line}, cột {column}: ", { line: problem.line, column: problem.column }) : ""}
            {translateBackend(problem.message)}
          </div>
        ))}
      </Alert>
    );
  }
  if (state === "unavailable") {
    return (
      <Alert severity="info" sx={{ mb: 1.5 }}>
        {t("Chưa kiểm được tệp lúc này, sơ đồ giữ bản gần nhất.")}
      </Alert>
    );
  }
  if (state === "waiting" || state === "checking") {
    return (
      <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
        {state === "checking" && <CircularProgress size={12} />}
        {state === "waiting" ? t("Sơ đồ cập nhật khi bạn ngừng gõ vài giây.") : t("Đang kiểm tệp…")}
      </Typography>
    );
  }
  return null;
}
