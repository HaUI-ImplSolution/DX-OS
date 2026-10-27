-- Optional explanation snapshot; old diagnoses remain unchanged.
ALTER TABLE diagnoses ADD COLUMN generated_explanation jsonb;
