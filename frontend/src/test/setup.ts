// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { vi } from "vitest";

// The language module reads the browser when it loads; the tests run in Node, in Vietnamese.
vi.mock("../shell/locale", () => ({ getLocale: () => "vi", setLocale: () => undefined, onLocaleChange: () => () => undefined }));
