-- SPDX-License-Identifier: Apache-2.0
-- Version 1 remains available so released history and its external anchors keep verifying.
ALTER TABLE record_event
  ADD COLUMN hash_version SMALLINT NOT NULL DEFAULT 1,
  ADD COLUMN actor_ref TEXT;

ALTER FUNCTION record_event_hash(TEXT, record_event) RENAME TO record_event_hash_v1;

-- New lines keep a stable pseudonymous actor commitment while the direct identity and free text
-- remain outside the immutable part of the event.
CREATE FUNCTION record_event_v2_fields() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.hash_version = 2 THEN
    NEW.actor_ref := encode(sha256(convert_to(NEW.actor, 'UTF8')), 'hex');
  END IF;
  RETURN NEW;
END
$$;

CREATE TRIGGER record_event_v2_fields_before_insert
BEFORE INSERT ON record_event
FOR EACH ROW EXECUTE FUNCTION record_event_v2_fields();

ALTER TABLE record_event
  ALTER COLUMN hash_version SET DEFAULT 2,
  ADD CONSTRAINT record_event_hash_version_valid CHECK (hash_version IN (1, 2)),
  ADD CONSTRAINT record_event_v2_actor_ref_present CHECK (hash_version = 1 OR actor_ref IS NOT NULL);

CREATE FUNCTION record_event_hash_v2(prev TEXT, e record_event) RETURNS TEXT
LANGUAGE sql STABLE AS $$
  SELECT encode(sha256(convert_to(coalesce(prev, '') || jsonb_build_array(e.hash_version, e.id,
    e.model, e.entity, e.record_id, e.action, e.actor_ref,
    to_char(e.at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), e.from_status,
    e.to_status)::text, 'UTF8')), 'hex')
$$;

-- Dispatch by the version stored on each line so one chain can contain both released v1 lines and
-- privacy-safe v2 lines without rewriting an existing hash.
CREATE FUNCTION record_event_hash(prev TEXT, e record_event) RETURNS TEXT
LANGUAGE sql STABLE AS $$
  SELECT CASE e.hash_version
    WHEN 1 THEN record_event_hash_v1(prev, e)
    WHEN 2 THEN record_event_hash_v2(prev, e)
  END
$$;
