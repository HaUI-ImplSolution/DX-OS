// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { afterEach, describe, expect, it, vi } from "vitest";
import { clearInboxReviews, getInboxReviews, inboxReviewSignal, saveInboxReview, selectInboxReview, subscribeInboxReviews, takeInboxReview } from "./inboxReview";

const batch = { page: 1, sent: ["a"], checked: ["a"], reviewed: 1, skipped: 0, flagged: new Map() };

afterEach(() => {
  clearInboxReviews();
  vi.useRealTimers();
});

describe("the review of the inbox", () => {
  it("keeps a batch for two minutes and tells its listeners", () => {
    vi.useFakeTimers();
    const listener = vi.fn();
    const stop = subscribeInboxReviews(listener);
    saveInboxReview(batch);
    expect(getInboxReviews()).toHaveLength(1);
    vi.advanceTimersByTime(120_001);
    expect(getInboxReviews()).toHaveLength(0);
    expect(listener).toHaveBeenCalledTimes(2);
    stop();
  });

  it("forgets everything and stops the running requests when cleared", () => {
    const signal = inboxReviewSignal();
    saveInboxReview(batch);
    clearInboxReviews();
    expect(signal.aborted).toBe(true);
    expect(getInboxReviews()).toHaveLength(0);
    expect(inboxReviewSignal().aborted).toBe(false);
  });

  it("hands the chosen task once, and only to its record", () => {
    const target = { model: "m", entity: "e", id: 3, task: "classify" };
    selectInboxReview(target);
    expect(takeInboxReview("m", "e", "3")).toBe("classify");
    expect(takeInboxReview("m", "e", 3)).toBeUndefined();
    selectInboxReview(target);
    expect(takeInboxReview("m", "e", 4)).toBeUndefined();
  });
});
