-- Who produced a diagnosis: the AI service or an engineer typing it in.
ALTER TABLE diagnoses ADD COLUMN source character varying(16) NOT NULL DEFAULT 'ENGINEER';
UPDATE diagnoses SET source = 'AI'
WHERE symptom_text IS NULL AND model_version <> 'manual-input-v1';
ALTER TABLE diagnoses
    ADD CONSTRAINT diagnoses_source_check CHECK (source IN ('AI', 'ENGINEER'));

CREATE OR REPLACE VIEW vw_ai_diagnosis_outcomes AS
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
  AND d.source = 'AI';
