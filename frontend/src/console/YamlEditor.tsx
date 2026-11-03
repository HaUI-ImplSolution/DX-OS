// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef } from "react";
import { Box, useTheme } from "@mui/material";
import { monaco } from "./monacoSetup";

interface Props {
  value: string;
  onChange: (text: string) => void;
  height?: number | string;
}

// A YAML editor with completion and errors from the schema of the model file.
export default function YamlEditor({ value, onChange, height = 520 }: Props) {
  const host = useRef<HTMLDivElement | null>(null);
  const editor = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const latest = useRef(onChange);
  latest.current = onChange;
  const dark = useTheme().palette.mode === "dark";

  useEffect(() => {
    if (!host.current) return undefined;
    const model = monaco.editor.createModel(value, "yaml", monaco.Uri.parse("inmemory://dxos/model.yaml"));
    editor.current = monaco.editor.create(host.current, {
      model,
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      tabSize: 2,
      theme: dark ? "vs-dark" : "vs",
    });
    const subscription = editor.current.onDidChangeModelContent(() => latest.current(editor.current?.getValue() ?? ""));
    return () => {
      subscription.dispose();
      editor.current?.dispose();
      model.dispose();
      editor.current = null;
    };
  }, []);

  useEffect(() => {
    if (editor.current && editor.current.getValue() !== value) editor.current.setValue(value);
  }, [value]);

  useEffect(() => {
    monaco.editor.setTheme(dark ? "vs-dark" : "vs");
  }, [dark]);

  return <Box ref={host} sx={{ height, border: 1, borderColor: "divider" }} />;
}
