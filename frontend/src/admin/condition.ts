// SPDX-License-Identifier: Apache-2.0
import type { FieldMeta, ProcessCondition } from "./types";

// A record value is compared as a number for the ordering operators and for a number field, and as text otherwise.
// A missing value never meets a condition.
export function conditionMet(condition: ProcessCondition | null | undefined, record: Record<string, unknown>): boolean {
  if (!condition) return true;
  const raw = record[condition.field];
  if (raw === null || raw === undefined || raw === "") return false;
  const numeric = typeof condition.value === "number";
  const left = numeric ? Number(raw) : String(raw);
  const right = condition.value;
  if (numeric && Number.isNaN(left)) return false;
  switch (condition.op) {
    case "==":
      return left === right;
    case "!=":
      return left !== right;
    case "<":
      return left < right;
    case "<=":
      return left <= right;
    case ">":
      return left > right;
    case ">=":
      return left >= right;
  }
}

// "Số tiền > 5.000.000": the label of the field when the entity has it, then the operator and the value.
export function conditionText(condition: ProcessCondition, fields: FieldMeta[] = []): string {
  const label = fields.find((field) => field.name === condition.field)?.label ?? condition.field;
  const value = typeof condition.value === "number" ? condition.value.toLocaleString("vi-VN") : condition.value;
  return `${label} ${condition.op} ${value}`;
}
