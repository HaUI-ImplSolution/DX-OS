// SPDX-License-Identifier: Apache-2.0
import polyglotI18nProvider from "ra-i18n-polyglot";
import viMessages from "./viMessages";

// The app has one language, so the provider never changes locale.
export const i18nProvider = polyglotI18nProvider(() => viMessages, "vi", { allowMissing: true });
