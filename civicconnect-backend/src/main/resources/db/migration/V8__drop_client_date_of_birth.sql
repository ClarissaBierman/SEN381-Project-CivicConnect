-- =====================================================================
-- V8: the client's date of birth is no longer stored (TD-03, ASR-005)
--
-- No requirement uses a requester's date of birth, so POPIA data minimisation says not to keep it.
-- Employee date of birth and address stay: CivicConnect has no other HR or employee record.
-- The column grants on it (V3) are dropped with the column. Audit rows written before V8 still hold
-- the old value in their JSON (the audit trail is append-only); the audit_log view (V6) masks it.
-- Never edit this file after it has run anywhere - add V9 instead (DEC-008).
-- =====================================================================

ALTER TABLE client DROP COLUMN date_of_birth;
