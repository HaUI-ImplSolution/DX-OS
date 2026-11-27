// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { translateBackend, translateData } from "./backendMessages";
import { errorMessageFor, registerErrorMessages, statusMessageFor } from "./errorMessages";

describe("messages of the backend", () => {
  it("keeps the English text for another language and an unknown message", () => {
    expect(translateBackend("Something new", "en")).toBe("Something new");
    expect(translateBackend("Something new")).toBe("Something new");
    expect(translateBackend("")).toBe("");
    expect(translateBackend("a new problem; another one")).toBe("a new problem; another one");
  });

  it("translates the position of a problem in a model file", () => {
    expect(translateBackend("line 3, column 5: Something new")).toBe("dòng 3, cột 5: Something new");
  });

  it("translates lists and the fields of a form, and leaves other values", () => {
    expect(translateData(["Something new", 2])).toEqual(["Something new", 2]);
    expect(translateData({ title: "Something new", rows: 2 })).toEqual({ title: "Something new", rows: 2 });
    expect(translateData(null)).toBeNull();
  });

  it("prefers a registered message of an error code", () => {
    registerErrorMessages({ ERR_TEST: "Thông báo thử" });
    expect(errorMessageFor("ERR_TEST", "raw")).toBe("Thông báo thử");
    expect(errorMessageFor(undefined, "raw")).toBe("raw");
    expect(errorMessageFor("ERR_UNKNOWN", "raw")).toBe("raw");
    expect(statusMessageFor(500)).toContain("500");
  });
});
