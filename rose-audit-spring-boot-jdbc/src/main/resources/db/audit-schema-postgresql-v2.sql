ALTER TABLE audit_event
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(32);

UPDATE audit_event
SET event_type = 'UNKNOWN'
WHERE event_type IS NULL;

ALTER TABLE audit_event
    ALTER COLUMN event_type SET DEFAULT 'UNKNOWN',
    ALTER COLUMN event_type SET NOT NULL;

ALTER TABLE audit_event
    ALTER COLUMN duration_ms DROP NOT NULL;
