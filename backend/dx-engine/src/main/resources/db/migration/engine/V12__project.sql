-- SPDX-License-Identifier: Apache-2.0
-- Projects: a name for the group of models and service clients that one integration uses.
-- A model belongs to at most one project, and so does a client; the grouping is for browsing and
-- judging impact and does not change who can read what. A project that still holds a model or a
-- client cannot be deleted.

CREATE TABLE project (
  name        TEXT        PRIMARY KEY,
  label       TEXT        NOT NULL,
  description TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_project (
  model   TEXT PRIMARY KEY,
  project TEXT NOT NULL REFERENCES project (name)
);

-- client_id is the full Keycloak client id, dxos-app-<name>.
CREATE TABLE client_project (
  client_id TEXT PRIMARY KEY,
  project   TEXT NOT NULL REFERENCES project (name)
);

CREATE INDEX model_project_project ON model_project (project);
CREATE INDEX client_project_project ON client_project (project);
