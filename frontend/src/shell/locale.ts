// SPDX-License-Identifier: Apache-2.0
// The language of the interface, Vietnamese or English. The choice is kept in the browser and read
// before anything is drawn; changing it reloads the page so that every screen starts in the new language.
export type Locale = "vi" | "en";

const STORAGE_KEY = "dxos.locale";

function stored(): Locale {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    if (value === "vi" || value === "en") return value;
  } catch {
    // Storage can be blocked; the default language stays.
  }
  return "vi";
}

const current: Locale = stored();
document.documentElement.lang = current;

export const getLocale = (): Locale => current;

export function setLocale(locale: Locale) {
  if (locale === current) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // Without storage the page reloads in the same language.
  }
  window.location.reload();
}
