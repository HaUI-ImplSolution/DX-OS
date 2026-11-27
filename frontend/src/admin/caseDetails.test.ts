// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it, vi } from "vitest";

// The screens read the browser address through the sign-in module when they load.
vi.mock("../shell/auth", () => ({ getToken: async () => null, clearSession: () => undefined }));

import type { EscalationLevel } from "../console/api";
import { escalatedDetail, skippedReason, updatedDetail, userIdsOf } from "./caseParts";
import type { FieldMeta, RecordEvent } from "./types";

const level = (to: string[], times: number) => ({ to, times }) as unknown as EscalationLevel;
const label = (name: string) => name.toUpperCase();
const field = (name: string, type: FieldMeta["type"]) => ({ name, label: name, type }) as FieldMeta;

describe("escalatedDetail", () => {
  const levels = [level(["MANAGER"], 1), level(["DIRECTOR", "HEAD"], 3)];

  it("names who was told and the round when the level repeats", () => {
    expect(escalatedDetail("2:1", levels)).toBe("Báo DIRECTOR, HEAD: cấp 2, lần 2/3");
  });

  it("leaves out the round of a level that notifies once", () => {
    expect(escalatedDetail("1:0", levels)).toBe("Báo MANAGER: cấp 1");
  });

  it("still shows the level when the model no longer has it", () => {
    expect(escalatedDetail("5:0", levels)).toBe("Cấp 5, lần 1");
  });

  it("is nothing for a detail that is not a level and round", () => {
    expect(escalatedDetail("oops", levels)).toBeNull();
  });
});

describe("skippedReason", () => {
  it("says which user field was empty", () => {
    expect(skippedReason("add_to_group skipped: the record has no user in field applicant", label)).toBe(
      'Thêm vào nhóm bị bỏ qua: hồ sơ chưa có người dùng ở trường "APPLICANT"',
    );
  });

  it("says which group field was empty, or that there is no group", () => {
    expect(skippedReason("remove_from_group skipped: the record has no group in field team", label)).toContain('nhóm ở trường "TEAM"');
    expect(skippedReason("remove_from_group skipped: the record has no group", label)).toContain("chưa có nhóm");
  });

  it("passes an unknown text on", () => {
    expect(skippedReason("something else", label)).toBe("something else");
  });
});

describe("updatedDetail", () => {
  const fields = [field("assignee", "user"), field("due", "datetime"), field("title", "text")];

  it("is nothing without a detail", () => {
    expect(updatedDetail(null, fields, label, {})).toBeNull();
  });

  it("names the fields of an older detail", () => {
    expect(updatedDetail("title, due", fields, label, {})).toBe("TITLE, DUE");
  });

  it("shows each change as old to new, with a dash for no value and a name for a user", () => {
    const detail = JSON.stringify({ title: [null, "B"], assignee: ["u1", "u2"], size: ["", "3"] });
    expect(updatedDetail(detail, fields, label, { u1: "An", u2: "Bình" })).toBe(
      ["TITLE: — → B", "ASSIGNEE: An → Bình", "SIZE: — → 3"].join("\n"),
    );
  });

  it("keeps a user that has no known name, and a date that cannot be read", () => {
    const detail = JSON.stringify({ assignee: ["u9", null], due: ["not a date", null] });
    expect(updatedDetail(detail, fields, label, {})).toBe("ASSIGNEE: u9 → —\nDUE: not a date → —");
  });

  it("returns a broken detail as it is", () => {
    expect(updatedDetail("{oops", fields, label, {})).toBe("{oops");
  });
});

describe("userIdsOf", () => {
  const event = (action: string, detail: string | null) => ({ action, detail }) as RecordEvent;
  const detail = JSON.stringify({ assignee: ["u1", "u2"], title: ["a", "b"], reviewer: [null, "u3"] });

  it("collects the accounts moved between in user fields", () => {
    expect(userIdsOf(event("updated", detail), new Set(["assignee", "reviewer"]))).toEqual(["u1", "u2", "u3"]);
  });

  it("is empty for another action, a plain detail, no user field or a broken detail", () => {
    expect(userIdsOf(event("created", detail), new Set(["assignee"]))).toEqual([]);
    expect(userIdsOf(event("updated", "assignee"), new Set(["assignee"]))).toEqual([]);
    expect(userIdsOf(event("updated", detail), new Set())).toEqual([]);
    expect(userIdsOf(event("updated", "{oops"), new Set(["assignee"]))).toEqual([]);
  });
});
