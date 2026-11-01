-- SPDX-License-Identifier: Apache-2.0
-- Bookkeeping of the model-driven service: applied model versions and the history of each run.

CREATE TABLE model_version (
  id         BIGSERIAL PRIMARY KEY,
  model      TEXT        NOT NULL,
  version    INT         NOT NULL,
  hash       TEXT        NOT NULL,
  content    TEXT        NOT NULL,
  applied_by TEXT        NOT NULL,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (model, version)
);

CREATE TABLE apply_run (
  id          BIGSERIAL PRIMARY KEY,
  model       TEXT        NOT NULL,
  hash        TEXT        NOT NULL,
  status      TEXT        NOT NULL CHECK (status IN ('running', 'done', 'failed')),
  actor       TEXT        NOT NULL,
  started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ
);

CREATE TABLE apply_step (
  id     BIGSERIAL PRIMARY KEY,
  run_id BIGINT      NOT NULL REFERENCES apply_run (id) ON DELETE CASCADE,
  seq    INT         NOT NULL,
  kind   TEXT        NOT NULL,
  target TEXT        NOT NULL,
  status TEXT        NOT NULL CHECK (status IN ('done', 'failed')),
  error  TEXT,
  at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX apply_step_run ON apply_step (run_id, seq);
