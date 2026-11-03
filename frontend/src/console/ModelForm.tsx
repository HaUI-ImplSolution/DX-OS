// SPDX-License-Identifier: Apache-2.0
import { useMemo } from "react";
import Form from "@rjsf/mui";
import type { RJSFSchema } from "@rjsf/utils";
import { customizeValidator } from "@rjsf/validator-ajv8";
import Ajv2020 from "ajv/dist/2020";

// The schema of the model file is JSON Schema 2020-12.
const validator = customizeValidator({ AjvClass: Ajv2020 });

interface Props {
  schema: Record<string, unknown>;
  value: Record<string, unknown>;
  onChange: (value: Record<string, unknown>) => void;
}

// A form built from the schema of the model file, so a new key in the schema is a new field here.
export default function ModelForm({ schema, value, onChange }: Props) {
  const rjsf = useMemo(() => typed(schema) as RJSFSchema, [schema]);
  return (
    <Form
      schema={rjsf}
      formData={value}
      validator={validator}
      liveValidate
      experimental_defaultFormStateBehavior={{ arrayMinItems: { populate: "requiredOnly" }, emptyObjectFields: "skipDefaults" }}
      showErrorList={false}
      uiSchema={{ "ui:submitButtonOptions": { norender: true } }}
      onChange={(event) => onChange((event.formData ?? {}) as Record<string, unknown>)}
    />
  );
}

// The form needs a type on every node; the model schema leaves it out where a constant or a list of
// values already says it.
function typed(node: unknown): unknown {
  if (Array.isArray(node)) return node.map(typed);
  if (node === null || typeof node !== "object") return node;
  const copy: Record<string, unknown> = {};
  Object.entries(node).forEach(([key, value]) => (copy[key] = typed(value)));
  if (!("type" in copy) && "const" in copy) copy.type = typeof copy.const === "number" ? "integer" : "string";
  if (!("type" in copy) && "enum" in copy) copy.type = "string";
  return copy;
}
