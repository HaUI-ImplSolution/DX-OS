// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import * as monaco from "monaco-editor";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { configureMonacoYaml, type MonacoYaml } from "monaco-yaml";
import yamlWorker from "monaco-yaml/yaml.worker?worker";

self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker();
  },
};

type LegacyWorkerOptions = { createData?: unknown; host?: Record<string, Function>; keepIdleModels?: boolean };

// monaco-yaml asks for its worker with the createWebWorker options of monaco-editor before 0.57
// (label, moduleId, createData); since 0.57 the caller starts the worker and sends the creation data.
const yamlMonaco = {
  ...monaco,
  editor: {
    ...monaco.editor,
    createWebWorker(options: LegacyWorkerOptions) {
      const worker = new yamlWorker();
      worker.postMessage("ignore");
      worker.postMessage(options.createData);
      return monaco.editor.createWebWorker({ worker, host: options.host, keepIdleModels: options.keepIdleModels });
    },
  },
} as unknown as typeof monaco;

let handle: MonacoYaml | null = null;

// Points the YAML language service at the schema of the model file; later calls replace it.
export function useModelSchema(schema: Record<string, unknown>) {
  const config = {
    enableSchemaRequest: false,
    validate: true,
    completion: true,
    hover: true,
    schemas: [{ uri: "https://dxos.local/model-v1.schema.json", fileMatch: ["*"], schema }],
  };
  if (handle) void handle.update(config);
  else handle = configureMonacoYaml(yamlMonaco, config);
}

export { monaco };
