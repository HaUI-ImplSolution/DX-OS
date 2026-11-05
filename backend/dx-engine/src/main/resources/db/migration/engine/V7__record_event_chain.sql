-- SPDX-License-Identifier: Apache-2.0
-- Each history line carries the SHA-256 of the line before it and of its own content, so a line
-- that is changed, removed or slipped in later breaks the chain. Lines written before this
-- migration are linked when the service starts.
ALTER TABLE record_event
  ADD COLUMN prev_hash TEXT,
  ADD COLUMN hash      TEXT;

-- The hash of a line given the hash of the line before it. The time is written in UTC so the
-- result does not depend on the time zone of the session.
CREATE FUNCTION record_event_hash(prev TEXT, e record_event) RETURNS TEXT
LANGUAGE sql STABLE AS $$
  SELECT encode(sha256(convert_to(coalesce(prev, '') || jsonb_build_array(e.id, e.model, e.entity,
    e.record_id, e.action, e.actor, e.actor_name,
    to_char(e.at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), e.from_status,
    e.to_status, e.note, e.detail)::text, 'UTF8')), 'hex')
$$;
