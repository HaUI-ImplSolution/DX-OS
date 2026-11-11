-- SPDX-License-Identifier: Apache-2.0
-- The files attached to the records of a model. The bytes live in object storage under object_key;
-- a file field of the model holds the name of the latest one.

CREATE TABLE attachment (
  id           BIGSERIAL PRIMARY KEY,
  model        TEXT        NOT NULL,
  entity       TEXT        NOT NULL,
  record_id    BIGINT      NOT NULL,
  field        TEXT        NOT NULL,
  object_key   TEXT        NOT NULL,
  filename     TEXT        NOT NULL,
  content_type TEXT        NOT NULL,
  size         BIGINT      NOT NULL,
  uploaded_by  TEXT        NOT NULL,
  uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX attachment_record ON attachment (model, entity, record_id, id);
