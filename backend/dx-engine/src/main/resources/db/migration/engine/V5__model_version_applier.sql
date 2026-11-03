-- SPDX-License-Identifier: Apache-2.0
-- The name of who applied a version, as the token gave it at that time; applied_by keeps the user id.
ALTER TABLE model_version ADD COLUMN applied_by_name TEXT;
