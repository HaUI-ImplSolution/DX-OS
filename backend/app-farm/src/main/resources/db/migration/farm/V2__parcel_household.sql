-- Parcels belong to a household (workspace owned by the core).
ALTER TABLE parcels ADD COLUMN household_id bigint NOT NULL REFERENCES households (id);
CREATE INDEX idx_parcels_household_id ON parcels (household_id);
