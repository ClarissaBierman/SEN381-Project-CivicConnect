-- =====================================================================
-- V5: status history of one request, for the requester's "Updates" list (FR-003, FR-007).
-- Requesters may not read audit_entry (it holds staff names and other people's data), so this
-- function returns only the status and the time of each status change of one request.
-- The application checks that the caller may see the request before calling it.
-- Never edit this file after it has run anywhere - add V6 instead (DEC-008).
-- =====================================================================

CREATE OR REPLACE FUNCTION request_status_history(p_req_id BIGINT)
RETURNS TABLE (status VARCHAR, changed_at TIMESTAMP)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = civicconnect, pg_temp
AS $$
    SELECT CAST(a.new_data AS jsonb) ->> 'status', a.changed_at
    FROM audit_entry a
    WHERE a.table_name = 'request'
      AND a.record_id = p_req_id::text
      AND a.new_data IS NOT NULL
      AND (a.operation = 'INSERT'
           OR CAST(a.old_data AS jsonb) ->> 'status' IS DISTINCT FROM CAST(a.new_data AS jsonb) ->> 'status')
    ORDER BY a.changed_at, a.aud_id
$$;

REVOKE ALL ON FUNCTION request_status_history(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION request_status_history(BIGINT)
    TO civicconnect_client, civicconnect_staff, civicconnect_management;
