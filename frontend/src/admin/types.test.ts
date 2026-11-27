// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { resourceName, splitResource } from "./types";

describe("resource names", () => {
  it("joins the model and the entity with a hyphen and splits them back", () => {
    expect(resourceName("support", "ticket")).toBe("support-ticket");
    expect(splitResource("support-ticket")).toEqual({ model: "support", entity: "ticket" });
  });

  it("splits at the first hyphen only", () => {
    expect(splitResource("a-b-c")).toEqual({ model: "a", entity: "b-c" });
  });
});
