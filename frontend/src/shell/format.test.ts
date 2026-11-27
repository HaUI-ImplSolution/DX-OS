// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { formatDate, formatDuration, formatNumber, timeAgo } from "./format";

describe("formatNumber and formatDate", () => {
  it("groups a number and leaves anything else as it is", () => {
    expect(formatNumber(1500000)).toBe("1.500.000");
    expect(formatNumber("1500000")).toBe("1.500.000");
    expect(formatNumber("abc")).toBe("abc");
    expect(formatNumber("")).toBe("");
  });

  it("writes a date without a time zone moving it", () => {
    expect(formatDate("2026-11-20")).toBe("20/11/2026");
    expect(formatDate("2026-02-31")).toBe("2026-02-31");
    expect(formatDate("not a date")).toBe("not a date");
  });
});

describe("formatDuration", () => {
  it("shows the two largest units", () => {
    expect(formatDuration(45)).toBe("45 giây");
    expect(formatDuration(25 * 60)).toBe("25 phút");
    expect(formatDuration(2 * 3600 + 5 * 60)).toBe("2 giờ 5 phút");
    expect(formatDuration(3 * 86400 + 4 * 3600)).toBe("3 ngày 4 giờ");
    expect(formatDuration(-5)).toBe("0 giây");
  });
});

describe("timeAgo", () => {
  const now = Date.parse("2026-11-25T12:00:00Z");

  it("counts from the nearest unit", () => {
    expect(timeAgo("2026-11-25T11:59:30Z", now)).toBe("vừa xong");
    expect(timeAgo("2026-11-25T11:30:00Z", now)).toBe("30 phút trước");
    expect(timeAgo("2026-11-25T09:00:00Z", now)).toBe("3 giờ trước");
    expect(timeAgo("2026-11-22T12:00:00Z", now)).toBe("3 ngày trước");
    expect(timeAgo(null, now)).toBe("");
  });
});
