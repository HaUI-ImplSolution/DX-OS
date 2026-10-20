-- Knowledge passages the AI retrieved for a diagnosis, kept as shown to the engineer.
ALTER TABLE diagnoses ADD COLUMN knowledge_evidence jsonb NOT NULL DEFAULT '[]';
ALTER TABLE diagnoses ALTER COLUMN recommendation_text DROP NOT NULL;
