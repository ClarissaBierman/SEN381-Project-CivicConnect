-- =====================================================================
-- V3: database roles and permissions (DEC-007, ASR-001, ASR-005)
--
-- Same roles and grants as section 4 of database/civicconnect_supabase_build.sql, now under Flyway so
-- every environment (Supabase, CI, a new staging database) gets them. Written to be safe to run on a
-- database that already has them: roles are only created if missing, and GRANT/REVOKE can be repeated.
--
-- Must run as the schema owner (FLYWAY_USER, e.g. postgres) - see application.properties.
--
-- Changes compared with the build script:
--   * staff may read employee names (emp_id, first_name, surname, role, active) to show who handles
--     a request and who wrote a note - but not email, phone, address or password
--   * management may read client contact details (first_name, surname, email, phone, active) for
--     GET /clients/{id}/contact - but not date of birth or password
--
-- Never edit this file after it has run anywhere - add V5 instead (DEC-008).
-- =====================================================================

DO $$
BEGIN
    -- The login role is normally created (with its password) by the build script or a DBA. It is only
    -- created here, without LOGIN, so the grants below work on a fresh database. Then run once:
    --   ALTER ROLE civicconnect_app LOGIN PASSWORD '<the DB_PASSWORD from .env>';
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civicconnect_app') THEN
        CREATE ROLE civicconnect_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civicconnect_client') THEN
        CREATE ROLE civicconnect_client NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civicconnect_staff') THEN
        CREATE ROLE civicconnect_staff NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civicconnect_management') THEN
        CREATE ROLE civicconnect_management NOLOGIN;
    END IF;
END
$$;

-- civicconnect_app may switch to the three user roles (DbSession) but does not inherit their rights
GRANT civicconnect_client, civicconnect_staff, civicconnect_management
    TO civicconnect_app WITH INHERIT FALSE, SET TRUE;

-- Keep Supabase's public API roles out of this schema (they only exist on Supabase)
REVOKE ALL ON SCHEMA civicconnect FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA civicconnect FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA civicconnect FROM authenticated';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA civicconnect
    TO civicconnect_app, civicconnect_client, civicconnect_staff, civicconnect_management;

-- ---------- civicconnect_app (before login) ----------
GRANT SELECT ON client, employee TO civicconnect_app;         -- login, /auth/me, per-request account check
GRANT INSERT ON client TO civicconnect_app;                    -- registration (API-01)
GRANT USAGE ON SEQUENCE client_client_id_seq TO civicconnect_app;

-- ---------- civicconnect_client (requesters) ----------
GRANT SELECT ON category TO civicconnect_client;
GRANT SELECT, INSERT ON request TO civicconnect_client;       -- submit and read own requests (filtered by the app)
GRANT USAGE ON SEQUENCE request_req_id_seq, request_ref_seq TO civicconnect_client;
GRANT SELECT ON comment TO civicconnect_client;               -- only visible comments are returned by the app
GRANT SELECT, UPDATE ON client TO civicconnect_client;        -- own profile (filtered by the app)

-- ---------- civicconnect_staff (handlers) ----------
GRANT SELECT ON category, handler_category TO civicconnect_staff;
GRANT SELECT, UPDATE ON request TO civicconnect_staff;        -- status changes; no INSERT, no DELETE
GRANT SELECT, INSERT, UPDATE ON request_handler TO civicconnect_staff;
GRANT USAGE ON SEQUENCE request_handler_req_handler_id_seq TO civicconnect_staff;
GRANT SELECT, INSERT ON comment TO civicconnect_staff;
GRANT USAGE ON SEQUENCE comment_com_id_seq TO civicconnect_staff;
GRANT SELECT ON audit_entry TO civicconnect_staff;
GRANT SELECT (client_id) ON client TO civicconnect_staff;     -- requesters are shown by number only (ASR-005)
GRANT SELECT (emp_id, first_name, surname, role, active) ON employee TO civicconnect_staff;   -- NEW: names

-- ---------- civicconnect_management (managers) ----------
GRANT SELECT, INSERT, UPDATE ON category TO civicconnect_management;
GRANT USAGE ON SEQUENCE category_cat_id_seq TO civicconnect_management;
GRANT SELECT, INSERT, DELETE ON handler_category TO civicconnect_management;
GRANT USAGE ON SEQUENCE handler_category_handler_category_id_seq TO civicconnect_management;
GRANT SELECT, INSERT, UPDATE ON employee TO civicconnect_management;
GRANT USAGE ON SEQUENCE employee_emp_id_seq TO civicconnect_management;
GRANT SELECT, UPDATE ON request TO civicconnect_management;
GRANT SELECT, INSERT, UPDATE ON request_handler TO civicconnect_management;
GRANT USAGE ON SEQUENCE request_handler_req_handler_id_seq TO civicconnect_management;
GRANT SELECT, INSERT ON comment TO civicconnect_management;
GRANT USAGE ON SEQUENCE comment_com_id_seq TO civicconnect_management;
GRANT SELECT ON audit_entry TO civicconnect_management;
-- Management may activate/deactivate clients and read their contact details, never date of birth or password
GRANT SELECT (client_id, first_name, surname, email, phone, active) ON client TO civicconnect_management;  -- NEW: contact columns
GRANT UPDATE (active, updated_at) ON client TO civicconnect_management;
