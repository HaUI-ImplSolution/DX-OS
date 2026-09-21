-- Stable user ids (token "sub") next to the display names, and soft delete for records that can
-- be entered by mistake.
ALTER TABLE disease_reports ADD COLUMN reported_by_user_id character varying(255);
ALTER TABLE diagnoses ADD COLUMN decided_by_user_id character varying(255);

ALTER TABLE parcels ADD COLUMN deleted_at timestamp(6) with time zone;
ALTER TABLE crop_seasons ADD COLUMN deleted_at timestamp(6) with time zone;
ALTER TABLE disease_reports ADD COLUMN deleted_at timestamp(6) with time zone;

-- A deleted record must not block re-entering the same parcel code or a new active season.
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    WHERE con.conrelid = 'parcels'::regclass
      AND con.contype = 'u'
      AND (SELECT array_agg(a.attname::text)
           FROM pg_attribute a
           WHERE a.attrelid = con.conrelid AND a.attnum = ANY (con.conkey)) = ARRAY['code'];
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE parcels DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
CREATE UNIQUE INDEX uq_parcels_code_not_deleted ON parcels (code) WHERE deleted_at IS NULL;

DROP INDEX uq_crop_seasons_one_active_per_parcel;
CREATE UNIQUE INDEX uq_crop_seasons_one_active_per_parcel
ON crop_seasons (parcel_id)
WHERE status = 'ACTIVE' AND deleted_at IS NULL;
