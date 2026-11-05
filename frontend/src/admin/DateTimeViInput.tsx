// SPDX-License-Identifier: Apache-2.0
import { TextInput, required, type Validator } from "react-admin";

// The native date-time field follows the language of the browser, so this one is typed as 31/12/2026 14:30.
// The form keeps an ISO value (2026-12-31T14:30 in the local time zone) and the data provider turns it into an instant.
const TYPED = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/;
const ISO = /^\d{4}-\d{2}-\d{2}T/;
const pad = (value: number) => String(value).padStart(2, "0");

export const DATE_TIME_HINT = "Nhập theo dạng 31/12/2026 14:30";

// An ISO date-time or instant as the user reads it, in the local time zone; anything else as typed.
export function toTyped(value: unknown): string {
  if (typeof value !== "string" || !ISO.test(value)) return typeof value === "string" ? value : "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// A complete and real date-time becomes the ISO value; anything else stays as typed so the field can show the problem.
export function fromTyped(text: string): string {
  const match = TYPED.exec(text.trim());
  if (!match) return text;
  const [day, month, year, hour, minute] = match.slice(1).map(Number);
  const date = new Date(year, month - 1, day, hour, minute);
  const real =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day && date.getHours() === hour && date.getMinutes() === minute;
  return real ? `${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}` : text;
}

const typedCorrectly: Validator = (value) => (value && !ISO.test(String(value)) ? DATE_TIME_HINT : undefined);

export default function DateTimeViInput({ source, label, isRequired }: { source: string; label: string; isRequired: boolean }) {
  return (
    <TextInput
      source={source}
      label={label}
      placeholder="31/12/2026 14:30"
      format={toTyped}
      parse={fromTyped}
      validate={isRequired ? [required(), typedCorrectly] : typedCorrectly}
    />
  );
}
