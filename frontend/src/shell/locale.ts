// SPDX-License-Identifier: Apache-2.0
// The language of the interface. Vietnamese is the only one shipped; the rest of the app asks here, so
// a second language is a new table in backendMessages.ts and viMessages.ts plus a switch that calls setLocale.
export type Locale = "vi" | "en";

let current: Locale = "vi";

export const getLocale = (): Locale => current;
export const setLocale = (locale: Locale) => {
  current = locale;
};
