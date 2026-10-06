-- Runs after Hibernate's ddl-auto: update (spring.jpa.defer-datasource-initialization).
-- One ACTIVE crop season per parcel, enforced in the database as the second line of defence.
CREATE UNIQUE INDEX IF NOT EXISTS uq_crop_seasons_one_active_per_parcel
ON crop_seasons (parcel_id)
WHERE status = 'ACTIVE';
