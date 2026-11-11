-- SPDX-License-Identifier: Apache-2.0
-- What happened to each record of a model: who did what and when. Field values are not copied here.

CREATE TABLE record_event (
  id          BIGSERIAL PRIMARY KEY,
  model       TEXT        NOT NULL,
  entity      TEXT        NOT NULL,
  record_id   BIGINT      NOT NULL,
  action      TEXT        NOT NULL,
  actor       TEXT        NOT NULL,
  actor_name  TEXT,
  at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  from_status TEXT,
  to_status   TEXT,
  note        TEXT,
  detail      TEXT
);

CREATE INDEX record_event_record ON record_event (model, entity, record_id, id);
