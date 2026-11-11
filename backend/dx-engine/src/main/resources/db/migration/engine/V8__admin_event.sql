-- SPDX-License-Identifier: Apache-2.0
-- What administrators did: the apply of a model and the changes to accounts. A line is written
-- whether or not a webhook target exists, so a receiver can pull what it missed.

CREATE TABLE admin_event (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind       TEXT        NOT NULL,
  model      TEXT,
  actor      TEXT        NOT NULL,
  actor_name TEXT,
  at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  facts      JSONB       NOT NULL DEFAULT '{}'
);

CREATE INDEX admin_event_model ON admin_event (model, id);
