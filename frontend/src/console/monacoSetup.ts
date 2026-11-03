// SPDX-License-Identifier: Apache-2.0
import * as monaco from "monaco-editor";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { configureMonacoYaml, type MonacoYaml } from "monaco-yaml";
import yamlWorker from "monaco-yaml/yaml.worker?worker";

self.MonacoEnvironment = {
  getWorker(_workerId: string, label: string) {
    return label === "yaml" ? new yamlWorker() : new editorWorker();
  },
};

let handle: MonacoYaml | null = null;

// Points the YAML language service at the schema of the model file; later calls replace it.
export function useModelSchema(schema: Record<string, unknown>) {
  const config = {
    enableSchemaRequest: false,
    validate: true,
    completion: true,
    hover: true,
    schemas: [{ uri: "https://dxos.local/model-v0.schema.json", fileMatch: ["*"], schema }],
  };
  if (handle) void handle.update(config);
  else handle = configureMonacoYaml(monaco, config);
}

export { monaco };
