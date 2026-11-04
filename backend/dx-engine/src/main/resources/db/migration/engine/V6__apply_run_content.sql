-- SPDX-License-Identifier: Apache-2.0
-- What a run was asked to apply: the file, the renames and the level that was accepted. An apply that
-- stopped can then be finished or given up without the file.
ALTER TABLE apply_run
  ADD COLUMN content TEXT,
  ADD COLUMN renames TEXT,
  ADD COLUMN accept  TEXT;
