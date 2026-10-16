-- =====================================================================
-- V6: database hardening after the M3 code review (D-18, D-19, D-21, D-26)
--
--  1. The application's login role may no longer create objects in the schema. Flyway runs as the
--     schema owner (FLYWAY_USER), so nothing needs it, and an object the app role created (e.g. its own
--     to_jsonb overload) could otherwise be picked up by a SECURITY DEFINER function and run as the owner.
--  2. Every SECURITY DEFINER function searches pg_catalog first and calls built-ins by their full name.
--  3. email_in_use(): checks both account tables, whatever the caller's role may read. A requester
--     changing their email no longer fails because their role cannot read the employee table.
--  4. audit_log: the only way staff and management read the audit trail. Staff only see request,
--     assignment and note history; client rows never show personal values (POPIA, ASR-005).
--  5. Row level security: requesters can only read and change their own account, their own requests
--     and the notes on their requests that are visible to them - also if a bug in a Java check misses it.
--  6. Staff may change their own password (API-26) and nothing else in their employee row.
--
-- Must run as the schema owner. Never edit this file after it has run anywhere - add V7 instead (DEC-008).
-- =====================================================================

-- ---------- 1. the app's login role cannot create objects ----------
REVOKE CREATE ON SCHEMA civicconnect FROM civicconnect_app;

-- ---------- 2. audit trigger: built-ins by full name, pg_catalog first ----------
CREATE OR REPLACE FUNCTION audit_row_change() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, civicconnect, pg_temp
AS $$
DECLARE
    v_old    jsonb;
    v_new    jsonb;
    v_emp    bigint;
    v_client bigint;
    v_id     text;
BEGIN
    IF TG_OP <> 'INSERT' THEN v_old := pg_catalog.to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN v_new := pg_catalog.to_jsonb(NEW); END IF;

    -- Nothing changed: no audit row
    IF TG_OP = 'UPDATE' AND v_old OPERATOR(pg_catalog.=) v_new THEN
        RETURN NULL;
    END IF;

    -- Never copy password hashes into the audit trail; just record that it changed
    IF TG_OP = 'UPDATE' AND (v_old OPERATOR(pg_catalog.->>) 'password') IS DISTINCT FROM (v_new OPERATOR(pg_catalog.->>) 'password') THEN
        v_new := v_new OPERATOR(pg_catalog.||) '{"password_changed": true}'::jsonb;
    END IF;
    v_old := v_old OPERATOR(pg_catalog.-) 'password'::text;
    v_new := v_new OPERATOR(pg_catalog.-) 'password'::text;

    v_id := COALESCE(v_new, v_old) OPERATOR(pg_catalog.->>) TG_ARGV[0];   -- TG_ARGV[0] = primary key column

    v_emp    := NULLIF(pg_catalog.current_setting('civicconnect.emp_id', true), '')::bigint;
    v_client := NULLIF(pg_catalog.current_setting('civicconnect.client_id', true), '')::bigint;
    IF v_emp IS NOT NULL THEN
        v_client := NULL;
    END IF;
    -- Registration: nobody is logged in yet, so the new client is the actor
    IF v_emp IS NULL AND v_client IS NULL AND TG_TABLE_NAME = 'client' AND TG_OP = 'INSERT' THEN
        v_client := (v_new OPERATOR(pg_catalog.->>) 'client_id')::bigint;
    END IF;

    INSERT INTO civicconnect.audit_entry (table_name, operation, record_id, old_data, new_data, emp_id, client_id)
    VALUES (TG_TABLE_NAME, TG_OP, v_id, v_old::text, v_new::text, v_emp, v_client);
    RETURN NULL;
END;
$$;

ALTER FUNCTION request_status_history(BIGINT) SET search_path = pg_catalog, civicconnect, pg_temp;

-- ---------- 3. is an email address taken by any account? ----------
CREATE OR REPLACE FUNCTION email_in_use(p_email TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, civicconnect, pg_temp
AS $$
    SELECT EXISTS (SELECT 1 FROM civicconnect.client c WHERE lower(c.email) = lower(p_email))
        OR EXISTS (SELECT 1 FROM civicconnect.employee e WHERE lower(e.email) = lower(p_email))
$$;

REVOKE ALL ON FUNCTION email_in_use(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION email_in_use(TEXT)
    TO civicconnect_app, civicconnect_client, civicconnect_staff, civicconnect_management;

-- ---------- 4. audit_log: what staff and management may read of the audit trail ----------
-- Personal values in client rows are replaced by "[hidden]" ("[hidden, changed]" on the new side of an
-- update), so a manager still sees THAT a requester changed their email, never the email itself.
CREATE OR REPLACE FUNCTION audit_mask(p_data JSONB, p_other JSONB, p_is_new BOOLEAN, p_keys TEXT[])
RETURNS JSONB
LANGUAGE sql
IMMUTABLE
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT CASE WHEN p_data IS NULL THEN NULL ELSE
        COALESCE((
            SELECT jsonb_object_agg(e.key,
                CASE WHEN e.key = ANY (p_keys) AND e.value <> 'null'::jsonb THEN
                    CASE WHEN p_is_new AND p_other IS NOT NULL AND (p_other -> e.key) IS DISTINCT FROM e.value
                         THEN to_jsonb('[hidden, changed]'::text)
                         ELSE to_jsonb('[hidden]'::text) END
                ELSE e.value END)
            FROM jsonb_each(p_data) e), '{}'::jsonb)
    END
$$;

REVOKE ALL ON FUNCTION audit_mask(JSONB, JSONB, BOOLEAN, TEXT[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit_mask(JSONB, JSONB, BOOLEAN, TEXT[]) TO civicconnect_staff, civicconnect_management;

CREATE OR REPLACE VIEW audit_log WITH (security_barrier = true) AS
SELECT a.aud_id,
       a.table_name,
       a.operation,
       a.record_id,
       CASE WHEN a.table_name = 'client'
            THEN audit_mask(CAST(a.old_data AS jsonb), CAST(a.new_data AS jsonb), false,
                            ARRAY['first_name', 'surname', 'email', 'phone', 'date_of_birth'])::text
            ELSE a.old_data END AS old_data,
       CASE WHEN a.table_name = 'client'
            THEN audit_mask(CAST(a.new_data AS jsonb), CAST(a.old_data AS jsonb), true,
                            ARRAY['first_name', 'surname', 'email', 'phone', 'date_of_birth'])::text
            ELSE a.new_data END AS new_data,
       a.emp_id,
       a.client_id,
       a.changed_at
FROM audit_entry a
-- Staff only need the history of requests (API-11); management reads every table (/audit/{resource})
WHERE a.table_name IN ('request', 'request_handler', 'comment')
   OR pg_has_role(current_user, 'civicconnect_management', 'USAGE');

REVOKE SELECT ON audit_entry FROM civicconnect_staff, civicconnect_management;
GRANT SELECT ON audit_log TO civicconnect_staff, civicconnect_management;

-- ---------- 5. row level security for requesters ----------
-- The logged-in requester's id, set per transaction by DbSession (empty for staff and the app role)
CREATE OR REPLACE FUNCTION current_client_id()
RETURNS BIGINT
LANGUAGE sql
STABLE
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT NULLIF(current_setting('civicconnect.client_id', true), '')::bigint
$$;

REVOKE ALL ON FUNCTION current_client_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION current_client_id()
    TO civicconnect_app, civicconnect_client, civicconnect_staff, civicconnect_management;

-- The logged-in employee's id (empty for requesters and the app role)
CREATE OR REPLACE FUNCTION current_emp_id()
RETURNS BIGINT
LANGUAGE sql
STABLE
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT NULLIF(current_setting('civicconnect.emp_id', true), '')::bigint
$$;

REVOKE ALL ON FUNCTION current_emp_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION current_emp_id()
    TO civicconnect_app, civicconnect_client, civicconnect_staff, civicconnect_management;

-- Policies only limit rows; the GRANTs from V3 still decide which commands and columns each role may use.
-- The schema owner (Flyway, the audit trigger, SECURITY DEFINER functions) is not affected.
ALTER TABLE client ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS client_own_account ON client;
DROP POLICY IF EXISTS client_other_roles ON client;
CREATE POLICY client_own_account ON client FOR ALL TO civicconnect_client
    USING (client_id = current_client_id())
    WITH CHECK (client_id = current_client_id());
CREATE POLICY client_other_roles ON client FOR ALL
    TO civicconnect_app, civicconnect_staff, civicconnect_management
    USING (true) WITH CHECK (true);

ALTER TABLE request ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS request_own_requests ON request;
DROP POLICY IF EXISTS request_employees ON request;
CREATE POLICY request_own_requests ON request FOR ALL TO civicconnect_client
    USING (requester_id = current_client_id())
    WITH CHECK (requester_id = current_client_id());
CREATE POLICY request_employees ON request FOR ALL TO civicconnect_staff, civicconnect_management
    USING (true) WITH CHECK (true);

ALTER TABLE comment ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS comment_visible_on_own_requests ON comment;
DROP POLICY IF EXISTS comment_employees ON comment;
CREATE POLICY comment_visible_on_own_requests ON comment FOR SELECT TO civicconnect_client
    USING (visible_to_requester
           AND EXISTS (SELECT 1 FROM request r WHERE r.req_id = comment.req_id
                                                AND r.requester_id = current_client_id()));
CREATE POLICY comment_employees ON comment FOR ALL TO civicconnect_staff, civicconnect_management
    USING (true) WITH CHECK (true);

-- ---------- 6. staff change their own password, and only that ----------
GRANT UPDATE (password) ON employee TO civicconnect_staff;

ALTER TABLE employee ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS employee_staff_read ON employee;
DROP POLICY IF EXISTS employee_staff_own_row ON employee;
DROP POLICY IF EXISTS employee_other_roles ON employee;
CREATE POLICY employee_staff_read ON employee FOR SELECT TO civicconnect_staff
    USING (true);                                   -- column grants still limit staff to names
CREATE POLICY employee_staff_own_row ON employee FOR UPDATE TO civicconnect_staff
    USING (emp_id = current_emp_id())
    WITH CHECK (emp_id = current_emp_id());
CREATE POLICY employee_other_roles ON employee FOR ALL TO civicconnect_app, civicconnect_management
    USING (true) WITH CHECK (true);
