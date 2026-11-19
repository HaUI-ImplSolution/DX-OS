-- SPDX-License-Identifier: Apache-2.0
-- Version 1 stays available so released history and its external anchors keep verifying.
-- A line written from now on uses version 2, whose hash leaves out the name, note and detail of
-- the line, so they are not part of the immutable chain.
ALTER TABLE record_event
  ADD COLUMN hash_version SMALLINT NOT NULL DEFAULT 1;

ALTER FUNCTION record_event_hash(TEXT, record_event) RENAME TO record_event_hash_v1;

ALTER TABLE record_event
  ALTER COLUMN hash_version SET DEFAULT 2,
  ADD CONSTRAINT record_event_hash_version_valid CHECK (hash_version IN (1, 2));

CREATE FUNCTION record_event_hash_v2(prev TEXT, e record_event) RETURNS TEXT
LANGUAGE sql STABLE AS $$
  SELECT encode(sha256(convert_to(coalesce(prev, '') || jsonb_build_array(e.hash_version, e.id,
    e.model, e.entity, e.record_id, e.action, e.actor,
    to_char(e.at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), e.from_status,
    e.to_status)::text, 'UTF8')), 'hex')
$$;

-- One chain can hold both versions, each line hashed by the version it carries. The calls are
-- qualified because the application runs this function with its own search_path, not Flyway's.
CREATE FUNCTION record_event_hash(prev TEXT, e record_event) RETURNS TEXT
LANGUAGE sql STABLE AS $$
  SELECT CASE e.hash_version
    WHEN 1 THEN engine.record_event_hash_v1(prev, e)
    WHEN 2 THEN engine.record_event_hash_v2(prev, e)
  END
$$;
