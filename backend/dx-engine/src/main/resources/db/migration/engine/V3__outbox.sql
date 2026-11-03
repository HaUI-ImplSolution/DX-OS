-- SPDX-License-Identifier: Apache-2.0
-- Messages that leave the system after a change commits. A row is written in the transaction of the
-- change, so a rolled-back change sends nothing; the idempotency key keeps one row per message.

CREATE TABLE outbox (
  id              BIGSERIAL PRIMARY KEY,
  topic           TEXT        NOT NULL,
  idempotency_key TEXT        NOT NULL UNIQUE,
  model           TEXT        NOT NULL,
  entity          TEXT        NOT NULL,
  record_id       BIGINT      NOT NULL,
  payload         JSONB       NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  attempts        INTEGER     NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  delivered_at    TIMESTAMPTZ,
  last_error      TEXT
);

CREATE INDEX outbox_pending ON outbox (next_attempt_at) WHERE delivered_at IS NULL;
