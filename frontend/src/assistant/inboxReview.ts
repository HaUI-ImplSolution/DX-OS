// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { ClassifierReview, RecordTarget } from "./api";

const REVIEW_TTL = 120_000;
type ReviewBatch = { page: number; sent: string[]; checked: string[]; reviewed: number; skipped: number; flagged: Map<string, ClassifierReview[]>; expires: number };

let batches: ReviewBatch[] = [];
let timer: ReturnType<typeof setTimeout> | undefined;
let session = new AbortController();
let selected: (RecordTarget & { task: string }) | undefined;
const listeners = new Set<() => void>();

export function subscribeInboxReviews(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export const getInboxReviews = () => batches;
export const inboxReviewSignal = () => session.signal;

function replaceBatches(next: ReviewBatch[]) {
  clearTimeout(timer);
  const now = Date.now();
  batches = next.filter(batch => batch.expires > now);
  timer = batches.length ? setTimeout(() => replaceBatches(batches), batches[0].expires - now) : undefined;
  for (const listener of listeners) listener();
}

export function saveInboxReview(batch: Omit<ReviewBatch, "expires">) {
  replaceBatches([...batches, { ...batch, expires: Date.now() + REVIEW_TTL }]);
}

export function clearInboxReviews() {
  session.abort();
  session = new AbortController();
  selected = undefined;
  replaceBatches([]);
}

export function selectInboxReview({ model, entity, id, task }: RecordTarget & { task: string }) {
  selected = { model, entity, id, task };
}

// Consume the selection once so restoring a browser history entry cannot create another proposal.
export function takeInboxReview(model: string, entity: string, id: number | string) {
  const review = selected;
  selected = undefined;
  if (review?.model === model && review.entity === entity && String(review.id) === String(id)) return review.task;
}
