// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { csvCell, toCsv } from "./csvExport";

describe("csvCell", () => {
  it.each(["=SUM(A1)", "+1", "-1", "@cmd", "\tx"])("writes %j as text so a spreadsheet does not run it", (value) => {
    expect(csvCell(value).replace(/^"/, "")).toMatch(/^'/);
  });

  it("keeps a value as it is when asked, such as a negative number", () => {
    expect(csvCell("-5", true)).toBe("-5");
  });

  it("quotes a cell with a comma, a quote or a line break and doubles its quotes", () => {
    expect(csvCell('a,"b"')).toBe('"a,""b"""');
    expect(csvCell("a\nb")).toBe('"a\nb"');
    expect(csvCell("plain")).toBe("plain");
  });
});

describe("toCsv", () => {
  it("starts with a byte order mark and ends every row with CRLF", () => {
    expect(toCsv([[{ text: "a" }, { text: "b" }], [{ text: "1" }, { text: "2" }]])).toBe("﻿a,b\r\n1,2\r\n");
  });
});
