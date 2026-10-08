-- Read-only views for BI (Superset): only approved or settled records, no personal data.
-- The BI role reads these views and nothing else, see SupersetReadOnlyRoleSeeder.

-- Approved, not deleted parcels. Owner name and household stay out.
CREATE VIEW vw_parcels_approved AS
SELECT p.id, p.code, p.area_hectares, p.latitude, p.longitude, p.location,
       p.gps_accuracy_meters, p.location_missing, p.created_at, p.updated_at
FROM parcels p
WHERE p.approval_status = 'APPROVED' AND p.deleted_at IS NULL;

-- Approved seasons of approved parcels.
CREATE VIEW vw_crop_seasons_approved AS
SELECT s.id, s.code, s.parcel_id, p.code AS parcel_code, s.status, s.season_type, s.season_year,
       s.rice_variety_code, s.rice_variety, s.start_date, s.expected_harvest_date,
       s.actual_harvest_date, s.harvested_at, s.yield_kg, s.harvested_area_hectares
FROM crop_seasons s
JOIN vw_parcels_approved p ON p.id = s.parcel_id
WHERE s.approval_status = 'APPROVED' AND s.deleted_at IS NULL;

-- Reports an engineer has already worked on; photo reference and reporter stay out.
CREATE VIEW vw_disease_reports_processed AS
SELECT r.id, r.crop_season_id, s.parcel_id, s.parcel_code, r.reported_at, r.quality_passed,
       r.quality_sharpness, r.quality_brightness
FROM disease_reports r
JOIN vw_crop_seasons_approved s ON s.id = r.crop_season_id
WHERE r.status = 'PROCESSED' AND r.deleted_at IS NULL;

-- Diagnoses an engineer approved, with the parcel position for maps.
CREATE VIEW vw_diagnoses_approved AS
SELECT d.id, d.disease_report_id, dis.code AS disease_code, dis.name AS disease_name,
       d.confidence, d.model_version, d.diagnosed_at, d.decided_at, r.crop_season_id,
       r.parcel_id, r.parcel_code, p.latitude, p.longitude
FROM diagnoses d
JOIN diseases dis ON dis.id = d.disease_id
JOIN vw_disease_reports_processed r ON r.id = d.disease_report_id
JOIN vw_parcels_approved p ON p.id = r.parcel_id
WHERE d.approval_status = 'APPROVED';

-- Spraying an engineer confirmed; harvest_locked_until is the isolation (PHI) end date.
CREATE VIEW vw_treatment_applications_confirmed AS
SELECT t.id, t.crop_season_id, s.parcel_id, s.parcel_code, pest.name AS pesticide_name,
       t.applied_at, t.phi_days_snapshot, t.harvest_locked_until, p.latitude, p.longitude
FROM treatment_applications t
JOIN pesticides pest ON pest.id = t.pesticide_id
JOIN vw_crop_seasons_approved s ON s.id = t.crop_season_id
JOIN vw_parcels_approved p ON p.id = s.parcel_id
WHERE t.confirmation_status = 'CONFIRMED';

-- How the AI fared against the engineer, one row per decided diagnosis the AI produced
-- (diagnoses typed in by an engineer are not AI output). CONFIRMED: approved as is; REJECTED:
-- rejected; OVERRIDDEN: a later diagnosis of the same report names another disease.
CREATE VIEW vw_ai_diagnosis_outcomes AS
SELECT d.id AS diagnosis_id, dis.code AS disease_code, d.model_version, d.confidence,
       d.decided_at,
       CASE
           WHEN d.approval_status = 'REJECTED' THEN 'REJECTED'
           WHEN later.disease_id IS NOT NULL AND later.disease_id <> d.disease_id THEN 'OVERRIDDEN'
           ELSE 'CONFIRMED'
       END AS outcome
FROM diagnoses d
JOIN diseases dis ON dis.id = d.disease_id
JOIN vw_disease_reports_processed r ON r.id = d.disease_report_id
LEFT JOIN LATERAL (
    SELECT l.disease_id
    FROM diagnoses l
    WHERE l.disease_report_id = d.disease_report_id AND l.id > d.id
      AND l.approval_status = 'APPROVED'
    ORDER BY l.id
    LIMIT 1
) later ON true
WHERE d.approval_status IN ('APPROVED', 'REJECTED')
  AND d.symptom_text IS NULL AND d.model_version <> 'manual-input-v1';
