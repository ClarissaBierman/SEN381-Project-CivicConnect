-- =====================================================================
-- V4: request priority (FR-016, CR-003, API-12)
-- Null until a manager sets it. Safe to run on a database where the column was already added by hand.
-- Never edit this file after it has run anywhere - add V5 instead (DEC-008).
-- =====================================================================

ALTER TABLE request ADD COLUMN IF NOT EXISTS priority VARCHAR(10);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_request_priority') THEN
        ALTER TABLE request ADD CONSTRAINT chk_request_priority
            CHECK (priority IS NULL OR priority IN ('LOW', 'MEDIUM', 'HIGH'));
    END IF;
END
$$;

-- Staff lists show priority, managers sort by it
CREATE INDEX IF NOT EXISTS idx_request_priority ON request (priority);
