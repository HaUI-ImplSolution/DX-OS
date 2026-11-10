-- SPDX-License-Identifier: Apache-2.0
-- The Idempotency-Key of a request that created a record or took an action: who sent it, what the
-- request was (its kind, target and a hash of the body) and the answer, kept for a while so that a
-- repeat gets the same answer instead of doing the work again.

CREATE TABLE idempotency (
  subject    TEXT        NOT NULL,
  key        TEXT        NOT NULL,
  request    TEXT        NOT NULL,
  response   JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (subject, key)
);

CREATE INDEX idempotency_created ON idempotency (created_at);
