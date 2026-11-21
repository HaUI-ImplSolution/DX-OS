-- SPDX-License-Identifier: Apache-2.0
-- Who belongs to a project, and who administers it. A member can be given the roles of the models
-- of the project; an administrator also manages the members. user_id is the Keycloak user id, with
-- no foreign key to it: a row of a deleted account is left out when read and removed when the
-- member is removed. Deleting a project deletes its members with it; a project that still holds
-- a model or a client is not deleted, as before.

CREATE TABLE project_member (
  project  TEXT        NOT NULL REFERENCES project (name) ON DELETE CASCADE,
  user_id  TEXT        NOT NULL,
  level    TEXT        NOT NULL CHECK (level IN ('MEMBER', 'ADMIN')),
  added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (project, user_id)
);

CREATE INDEX project_member_user ON project_member (user_id);
