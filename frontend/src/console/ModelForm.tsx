// SPDX-License-Identifier: Apache-2.0
import { useMemo } from "react";
import Form from "@rjsf/mui";
import { englishStringTranslator, TranslatableString, type RJSFSchema, type RJSFValidationError } from "@rjsf/utils";
import { customizeValidator } from "@rjsf/validator-ajv8";
import Ajv2020 from "ajv/dist/2020";

// The schema of the model file is JSON Schema 2020-12.
const validator = customizeValidator({ AjvClass: Ajv2020 });

// The text the form shows by itself, in Vietnamese; strings it has no entry for stay as they are.
const VI_STRINGS: Partial<Record<TranslatableString, string>> = {
  [TranslatableString.ArrayItemTitle]: "Mục",
  [TranslatableString.YesLabel]: "Có",
  [TranslatableString.NoLabel]: "Không",
  [TranslatableString.CloseLabel]: "Đóng",
  [TranslatableString.ErrorsLabel]: "Lỗi",
  [TranslatableString.NewStringDefault]: "Giá trị mới",
  [TranslatableString.AddButton]: "Thêm",
  [TranslatableString.AddItemButton]: "Thêm mục",
  [TranslatableString.CopyButton]: "Sao chép",
  [TranslatableString.MoveDownButton]: "Chuyển xuống",
  [TranslatableString.MoveUpButton]: "Chuyển lên",
  [TranslatableString.RemoveButton]: "Xóa",
  [TranslatableString.ClearLabel]: "Xóa trắng",
  [TranslatableString.OptionPrefix]: "Lựa chọn %1",
  [TranslatableString.KeyLabel]: "Khóa của %1",
};

function translateString(key: TranslatableString, params?: string[]): string {
  const text = VI_STRINGS[key];
  if (!text) return englishStringTranslator(key, params);
  return params ? params.reduce((result, value, index) => result.replace(`%${index + 1}`, value), text) : text;
}

// What the validator of the schema says, in Vietnamese.
function transformErrors(errors: RJSFValidationError[]): RJSFValidationError[] {
  return errors.map((error) => {
    const params = (error.params ?? {}) as Record<string, unknown>;
    switch (error.name) {
      case "required":
        return { ...error, message: "Bắt buộc nhập" };
      case "type":
        return { ...error, message: "Sai kiểu dữ liệu" };
      case "enum":
      case "const":
        return { ...error, message: "Phải là một trong các giá trị cho phép" };
      case "pattern":
        return { ...error, message: "Sai định dạng" };
      case "minItems":
        return { ...error, message: `Cần ít nhất ${String(params.limit)} mục` };
      case "minProperties":
        return { ...error, message: `Cần ít nhất ${String(params.limit)} mục` };
      case "minLength":
        return { ...error, message: `Cần ít nhất ${String(params.limit)} ký tự` };
      case "maxLength":
        return { ...error, message: `Chỉ được tối đa ${String(params.limit)} ký tự` };
      case "minimum":
        return { ...error, message: `Phải từ ${String(params.limit)} trở lên` };
      case "maximum":
        return { ...error, message: `Chỉ được tối đa ${String(params.limit)}` };
      case "additionalProperties":
        return { ...error, message: "Khóa này không có trong định dạng mô hình" };
      case "oneOf":
      case "anyOf":
        return { ...error, message: "Chưa khớp định dạng nào được phép" };
      default:
        return error;
    }
  });
}

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
      translateString={translateString}
      transformErrors={transformErrors}
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
