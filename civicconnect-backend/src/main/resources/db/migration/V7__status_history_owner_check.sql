-- =====================================================================
-- V7: request_status_history only returns a requester's own requests (D-36)
--
-- The V5 function is SECURITY DEFINER, so it reads audit_entry with the owner's rights and is not
-- limited by row level security. The application checks access before calling it, but V6 promises
-- that the database also holds if a Java check is missed. A requester (civicconnect.client_id set)
-- now gets rows only for a request they submitted; staff and management keep their own access checks.
-- Never edit this file after it has run anywhere - add V8 instead (DEC-008).
-- =====================================================================

CREATE OR REPLACE FUNCTION request_status_history(p_req_id BIGINT)
RETURNS TABLE (status VARCHAR, changed_at TIMESTAMP)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, civicconnect, pg_temp
AS $$
    SELECT CAST(a.new_data AS jsonb) ->> 'status', a.changed_at
    FROM civicconnect.audit_entry a
    WHERE a.table_name = 'request'
      AND a.record_id = p_req_id::text
      AND a.new_data IS NOT NULL
      AND (a.operation = 'INSERT'
           OR CAST(a.old_data AS jsonb) ->> 'status' IS DISTINCT FROM CAST(a.new_data AS jsonb) ->> 'status')
      AND (civicconnect.current_client_id() IS NULL
           OR EXISTS (SELECT 1 FROM civicconnect.request r
                      WHERE r.req_id = p_req_id AND r.requester_id = civicconnect.current_client_id()))
    ORDER BY a.changed_at, a.aud_id
$$;

REVOKE ALL ON FUNCTION request_status_history(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION request_status_history(BIGINT)
    TO civicconnect_client, civicconnect_staff, civicconnect_management;
