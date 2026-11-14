// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { en } from "./en";
import { getLocale } from "./locale";

// An action label of a process: a few words are both a state and an action, "Từ chối" is
// "Rejected" for a record and "Reject" for the button, so actions look up "<label>|action" first.
export const ta = (label: string): string => (getLocale() === "en" ? (en[`${label}|action`] ?? t(label)) : label);

// The text of the interface in the current language. The Vietnamese sentence is the key: Vietnamese
// shows it as written and English looks it up in the tables under ./en, falling back to the Vietnamese
// text when there is no entry. {name} in the sentence takes the value of params.name.
export function t(source: string, params?: Record<string, string | number>): string {
  let text = getLocale() === "en" ? (en[source] ?? source) : source;
  // An English entry of the form "{n} entity|{n} entities" has a singular and a plural, chosen by params.n.
  if (text.includes("|") && params && typeof params.n === "number") text = text.split("|")[params.n === 1 ? 0 : 1] ?? text;
  return params ? text.replace(/\{(\w+)\}/g, (whole, key: string) => (key in params ? String(params[key]) : whole)) : text;
}
