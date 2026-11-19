// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { FieldMeta, ProcessComparison, ProcessCondition } from "./types";
import { intlLocale } from "../shell/format";
import { t } from "../shell/t";

const isGroup = (condition: ProcessCondition): condition is Extract<ProcessCondition, { conditions: unknown }> =>
  "conditions" in condition;

// A record value is compared as a number for the ordering operators and for a number field, and as text otherwise.
// A missing value never meets a comparison, so "not" turns that into true.
export function conditionMet(condition: ProcessCondition | null | undefined, record: Record<string, unknown>): boolean {
  if (!condition) return true;
  if (isGroup(condition)) {
    if (condition.op === "and") return condition.conditions.every((part) => conditionMet(part, record));
    if (condition.op === "or") return condition.conditions.some((part) => conditionMet(part, record));
    return !conditionMet(condition.conditions[0], record);
  }
  return comparisonMet(condition, record);
}

function comparisonMet(condition: ProcessComparison, record: Record<string, unknown>): boolean {
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
// Joined comparisons read "A và (B hoặc C)".
export function conditionText(condition: ProcessCondition, fields: FieldMeta[] = []): string {
  if (isGroup(condition)) {
    if (condition.op === "not") return t("không {condition}", { condition: nested(condition.conditions[0], fields) });
    const word = condition.op === "and" ? t(" và ") : t(" hoặc ");
    return condition.conditions.map((part) => (condition.op === "and" && isOr(part) ? `(${conditionText(part, fields)})` : conditionText(part, fields))).join(word);
  }
  const label = fields.find((field) => field.name === condition.field)?.label ?? condition.field;
  const value = typeof condition.value === "number" ? condition.value.toLocaleString(intlLocale()) : condition.value;
  return `${label} ${condition.op} ${value}`;
}

const isOr = (condition: ProcessCondition) => isGroup(condition) && condition.op === "or";

const nested = (condition: ProcessCondition, fields: FieldMeta[]) =>
  isGroup(condition) && condition.op !== "not" ? `(${conditionText(condition, fields)})` : conditionText(condition, fields);

// The condition as it is written in the model file. The value of a comparison is quoted when comparisons are
// joined and it has spaces, parentheses or quotes in it, or is and, or or not.
export function conditionSource(condition: ProcessCondition | null | undefined, joined = false): string {
  if (!condition) return "";
  if (isGroup(condition)) {
    if (condition.op === "not") {
      const inner = condition.conditions[0];
      const text = conditionSource(inner, true);
      return `not ${isGroup(inner) && inner.op !== "not" ? `(${text})` : text}`;
    }
    return condition.conditions
      .map((part) => (condition.op === "and" && isOr(part) ? `(${conditionSource(part, true)})` : conditionSource(part, true)))
      .join(` ${condition.op} `);
  }
  const value = String(condition.value);
  const quoted = joined && typeof condition.value === "string" && (value === "" || /[\s()"']/.test(value) || ["and", "or", "not"].includes(value));
  return `${condition.field} ${condition.op} ${quoted ? `"${value}"` : value}`;
}
