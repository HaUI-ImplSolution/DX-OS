// SPDX-License-Identifier: Apache-2.0
// The language of the interface, Vietnamese or English. The choice is kept in the browser and read
// before anything is drawn; changing it reloads the page so that every screen starts in the new language.
// A screen that holds work not yet saved registers a check, so the switch can ask before reloading.
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

let unsavedCheck: (() => boolean) | null = null;

// Registers the check of the screen that holds unsaved work; the returned function removes it.
export function guardUnsavedWork(check: () => boolean): () => void {
  unsavedCheck = check;
  return () => {
    if (unsavedCheck === check) unsavedCheck = null;
  };
}

export const hasUnsavedWork = (): boolean => unsavedCheck?.() ?? false;
