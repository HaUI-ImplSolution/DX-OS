// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import polyglotI18nProvider from "ra-i18n-polyglot";
import englishMessages from "ra-language-english";
import { getLocale } from "./locale";
import viMessages from "./viMessages";

// The text react-admin shows by itself, in the language the person chose; the switch is LanguageSwitch.
export const i18nProvider = polyglotI18nProvider(
  (locale) => (locale === "en" ? englishMessages : viMessages),
  getLocale(),
  // No list of locales: react-admin would add a second language menu next to the VI | EN switch.
  [],
  { allowMissing: true },
);
