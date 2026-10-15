-- =====================================================================
-- CivicConnect - PRODUCTION database build (no dummy data, no default accounts)  (D-31)
-- Run once in the Supabase SQL editor of the PRODUCTION project, as the default "postgres" user.
--
--  1. Schema exactly as Flyway V1 + V2 would build it (same as civicconnect_supabase_build.sql)
--  2. The service categories (reference data, not test data)
--  3. Database roles for each type of user, with their permissions
--
-- Unlike civicconnect_supabase_build.sql it creates NO dummy clients, staff or requests and NO
-- default manager, because the production site is public and those passwords are in the repository.
-- The first manager is created by hand afterwards - see "First manager" at the end of this file.
--
-- WARNING: this DROPS the civicconnect schema and everything in it, then rebuilds it. Run it only
-- on a new, empty production project.
--
-- Before running: replace CHANGE_ME_to_a_long_random_password below with a new random password for
-- civicconnect_app (the value of DB_PASSWORD in Render). Do not commit the real value.
--
-- Afterwards the application (Flyway, baseline version 2) applies V3 onwards on its first start.
-- =====================================================================

BEGIN;

DROP SCHEMA IF EXISTS civicconnect CASCADE;
CREATE SCHEMA civicconnect;
SET LOCAL search_path TO civicconnect;

-- =====================================================================
-- 1a. V1__init_schema.sql (unchanged copy)
-- =====================================================================
CREATE TABLE client (
                        client_id BIGSERIAL PRIMARY KEY,
                        first_name VARCHAR(100) NOT NULL,
                        surname VARCHAR(100) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        password VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT now(),
                        date_of_birth DATE,
                        phone VARCHAR(30)
);

CREATE TABLE employee (
                          emp_id BIGSERIAL PRIMARY KEY,
                          first_name VARCHAR(100) NOT NULL,
                          surname VARCHAR(100) NOT NULL,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          password VARCHAR(255) NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT now(),
                          date_of_birth DATE,
                          street VARCHAR(255),
                          area VARCHAR(100),
                          city VARCHAR(100),
                          postal_code VARCHAR(20),
                          role VARCHAR(20) NOT NULL CHECK (role IN ('STAFF', 'MANAGEMENT')),
                          phone VARCHAR(30)
);

CREATE TABLE category (
                          cat_id BIGSERIAL PRIMARY KEY,
                          cat_name VARCHAR(100) NOT NULL UNIQUE,
                          active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE request (
                         req_id BIGSERIAL PRIMARY KEY,
                         ref_num VARCHAR(50) NOT NULL UNIQUE,
                         cat_id BIGINT NOT NULL REFERENCES category(cat_id),
                         description TEXT NOT NULL,
                         location VARCHAR(255),
                         attachment_url VARCHAR(500),
                         status VARCHAR(20) NOT NULL DEFAULT 'NEW'
                             CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
                         requester_id BIGINT NOT NULL REFERENCES client(client_id),
                         created_at TIMESTAMP NOT NULL DEFAULT now(),
                         closed_at TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE request_handler (
                                 req_id BIGINT NOT NULL REFERENCES request(req_id),
                                 emp_id BIGINT NOT NULL REFERENCES employee(emp_id),
                                 assigned_at TIMESTAMP NOT NULL DEFAULT now(),
                                 unassigned_at TIMESTAMP,
                                 PRIMARY KEY (req_id, emp_id)
);

CREATE TABLE audit_entry (
                             com_id BIGSERIAL PRIMARY KEY,
                             req_id BIGINT NOT NULL REFERENCES request(req_id),
                             entry_type VARCHAR(50) NOT NULL,
                             emp_id BIGINT NOT NULL REFERENCES employee(emp_id),
                             content TEXT,
                             created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_request_status ON request(status);
CREATE INDEX idx_request_requester ON request(requester_id);
CREATE INDEX idx_request_handler_emp ON request_handler(emp_id);
CREATE INDEX idx_audit_entry_request ON audit_entry(req_id);

-- =====================================================================
-- 1b. V2__align_with_entities.sql (unchanged copy)
-- =====================================================================
-- ---------- client / employee ----------
ALTER TABLE client   ADD COLUMN updated_at TIMESTAMP DEFAULT now();
ALTER TABLE client   ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE employee ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------- request ----------
UPDATE request SET location = 'Not provided' WHERE location IS NULL;
ALTER TABLE request ALTER COLUMN location SET NOT NULL;

UPDATE request SET closed_at = COALESCE(closed_at, updated_at) WHERE status IN ('CLOSED', 'REJECTED');
UPDATE request SET closed_at = NULL WHERE status NOT IN ('CLOSED', 'REJECTED');
-- closed_at is filled exactly when the request is Closed or Rejected
ALTER TABLE request ADD CONSTRAINT chk_request_closed_at
    CHECK ((status IN ('CLOSED', 'REJECTED')) = (closed_at IS NOT NULL));

-- Numbers for the reference number CC-<year>-<number>, generated by the server
CREATE SEQUENCE request_ref_seq;
SELECT setval('request_ref_seq', (SELECT COALESCE(MAX(req_id), 0) + 1 FROM request), false);

CREATE INDEX idx_request_cat_status_created ON request (cat_id, status, created_at);  -- staff list (FR-005, FR-006)
CREATE INDEX idx_request_requester_created  ON request (requester_id, created_at);   -- my requests (FR-003)

-- ---------- request_handler: own key, one active handler ----------
ALTER TABLE request_handler DROP CONSTRAINT request_handler_pkey;
ALTER TABLE request_handler ADD COLUMN req_handler_id BIGSERIAL PRIMARY KEY;
ALTER TABLE request_handler ADD CONSTRAINT chk_request_handler_dates
    CHECK (unassigned_at IS NULL OR unassigned_at >= assigned_at);
-- Integrity rule 2: only one active handler per request
CREATE UNIQUE INDEX ux_request_handler_active ON request_handler (req_id) WHERE unassigned_at IS NULL;

-- ---------- handler_category (FR-005, DEC-002) ----------
CREATE TABLE handler_category (
    handler_category_id BIGSERIAL PRIMARY KEY,
    emp_id              BIGINT NOT NULL REFERENCES employee(emp_id),
    cat_id              BIGINT NOT NULL REFERENCES category(cat_id),
    CONSTRAINT uq_handler_category UNIQUE (emp_id, cat_id)   -- a handler is linked to a category once
);
CREATE INDEX idx_handler_category_cat ON handler_category (cat_id);

-- ---------- comment (FR-010) ----------
CREATE TABLE comment (
    com_id               BIGSERIAL PRIMARY KEY,
    req_id               BIGINT        NOT NULL REFERENCES request(req_id),
    emp_id               BIGINT        NOT NULL REFERENCES employee(emp_id),
    content              VARCHAR(2000) NOT NULL,
    visible_to_requester BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_comment_req_created ON comment (req_id, created_at);

-- ---------- audit_entry: replaced by the row-level change log ----------
DROP TABLE audit_entry;
CREATE TABLE audit_entry (
    aud_id     BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    operation  VARCHAR(15) NOT NULL,           -- INSERT / UPDATE / DELETE
    record_id  VARCHAR(20) NOT NULL,           -- primary key of the changed row
    old_data   TEXT,                           -- JSON of the row before the change (null for INSERT)
    new_data   TEXT,                           -- JSON of the row after the change (null for DELETE)
    emp_id     BIGINT REFERENCES employee(emp_id),
    client_id  BIGINT REFERENCES client(client_id),
    changed_at TIMESTAMP NOT NULL DEFAULT now(),
    -- A1: at most one actor (both null = system change, e.g. a migration)
    CONSTRAINT chk_audit_one_actor CHECK (num_nonnulls(emp_id, client_id) <= 1)
);
CREATE INDEX idx_audit_entry_table_record ON audit_entry (table_name, record_id, changed_at);
CREATE INDEX idx_audit_entry_changed_at   ON audit_entry (changed_at);

-- ---------- audit trigger (DEC-007) ----------
-- Writes one audit_entry row for every INSERT, UPDATE and DELETE on the audited tables.
-- The actor comes from transaction-local settings the application sets (DbSession.begin):
--   civicconnect.emp_id / civicconnect.client_id
-- Password columns are always removed from the copied data.
CREATE FUNCTION audit_row_change() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
AS $$
DECLARE
    v_old    jsonb;
    v_new    jsonb;
    v_emp    bigint;
    v_client bigint;
    v_id     text;
BEGIN
    IF TG_OP <> 'INSERT' THEN v_old := to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN v_new := to_jsonb(NEW); END IF;

    -- Nothing changed: no audit row
    IF TG_OP = 'UPDATE' AND v_old = v_new THEN
        RETURN NULL;
    END IF;

    -- Never copy password hashes into the audit trail; just record that it changed
    IF TG_OP = 'UPDATE' AND (v_old ->> 'password') IS DISTINCT FROM (v_new ->> 'password') THEN
        v_new := v_new || '{"password_changed": true}'::jsonb;
    END IF;
    v_old := v_old - 'password';
    v_new := v_new - 'password';

    v_id := COALESCE(v_new, v_old) ->> TG_ARGV[0];   -- TG_ARGV[0] = name of the primary key column

    v_emp    := NULLIF(current_setting('civicconnect.emp_id', true), '')::bigint;
    v_client := NULLIF(current_setting('civicconnect.client_id', true), '')::bigint;
    IF v_emp IS NOT NULL THEN
        v_client := NULL;
    END IF;
    -- Registration: nobody is logged in yet, so the new client is the actor
    IF v_emp IS NULL AND v_client IS NULL AND TG_TABLE_NAME = 'client' AND TG_OP = 'INSERT' THEN
        v_client := (v_new ->> 'client_id')::bigint;
    END IF;

    INSERT INTO audit_entry (table_name, operation, record_id, old_data, new_data, emp_id, client_id)
    VALUES (TG_TABLE_NAME, TG_OP, v_id, v_old::text, v_new::text, v_emp, v_client);
    RETURN NULL;
END;
$$;

-- Pin the function to this schema (SECURITY DEFINER functions must not trust the caller's search_path)
DO $$
BEGIN
    EXECUTE format('ALTER FUNCTION audit_row_change() SET search_path = %I, pg_temp', current_schema());
END
$$;

CREATE TRIGGER trg_audit_client           AFTER INSERT OR UPDATE OR DELETE ON client           FOR EACH ROW EXECUTE FUNCTION audit_row_change('client_id');
CREATE TRIGGER trg_audit_employee         AFTER INSERT OR UPDATE OR DELETE ON employee         FOR EACH ROW EXECUTE FUNCTION audit_row_change('emp_id');
CREATE TRIGGER trg_audit_category         AFTER INSERT OR UPDATE OR DELETE ON category         FOR EACH ROW EXECUTE FUNCTION audit_row_change('cat_id');
CREATE TRIGGER trg_audit_handler_category AFTER INSERT OR UPDATE OR DELETE ON handler_category FOR EACH ROW EXECUTE FUNCTION audit_row_change('handler_category_id');
CREATE TRIGGER trg_audit_request          AFTER INSERT OR UPDATE OR DELETE ON request          FOR EACH ROW EXECUTE FUNCTION audit_row_change('req_id');
CREATE TRIGGER trg_audit_request_handler  AFTER INSERT OR UPDATE OR DELETE ON request_handler  FOR EACH ROW EXECUTE FUNCTION audit_row_change('req_handler_id');
CREATE TRIGGER trg_audit_comment          AFTER INSERT OR UPDATE OR DELETE ON comment          FOR EACH ROW EXECUTE FUNCTION audit_row_change('com_id');

-- ---------- append-only tables (FR-010, DEC-007) ----------
CREATE FUNCTION block_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_audit_entry_append_only BEFORE UPDATE OR DELETE ON audit_entry FOR EACH ROW EXECUTE FUNCTION block_change();
CREATE TRIGGER trg_audit_entry_no_truncate BEFORE TRUNCATE ON audit_entry FOR EACH STATEMENT EXECUTE FUNCTION block_change();
CREATE TRIGGER trg_comment_append_only     BEFORE UPDATE OR DELETE ON comment FOR EACH ROW EXECUTE FUNCTION block_change();
CREATE TRIGGER trg_comment_no_truncate     BEFORE TRUNCATE ON comment FOR EACH STATEMENT EXECUTE FUNCTION block_change();


-- =====================================================================
-- 2. Service categories (reference data). The audit trigger is on, so each is recorded as a
--    system change. Management can rename, add or deactivate categories in the app later.
-- =====================================================================
INSERT INTO category (cat_name, active) VALUES
    ('Facility Faults',   TRUE),
    ('Damaged Equipment', TRUE),
    ('Security Concerns', TRUE),
    ('IT Support',        TRUE),
    ('Maintenance',       TRUE),
    ('Lost Property',     TRUE),
    ('Other',             TRUE);

-- =====================================================================
-- 4. Database roles and permissions
--
--   civicconnect_app         LOGIN role Spring Boot connects as. On its own it can only do what
--                            is needed before anyone is logged in: log in (read accounts),
--                            register a client, and let Flyway keep its history table.
--   civicconnect_client      requesters  (Client)
--   civicconnect_staff       handlers    (Employee role STAFF)
--   civicconnect_management  managers    (Employee role MANAGEMENT)
--
-- For every API call, DbSession switches the connection to the role of the logged-in user for
-- that one transaction (set_config('role', ..., true)). civicconnect_app is a member of the three
-- user roles WITHOUT inheriting their rights, so it only gets them by switching.
-- No role can DELETE anything except Management removing a handler's category.
-- Audit rows are written by the trigger function (SECURITY DEFINER), so no role inserts into
-- audit_entry directly, and nobody can UPDATE or DELETE comment or audit_entry rows.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civicconnect_app') THEN
        -- Change this password, then put the same value in DB_PASSWORD in your .env
        CREATE ROLE civicconnect_app LOGIN PASSWORD 'CHANGE_ME_to_a_long_random_password';
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

GRANT civicconnect_client, civicconnect_staff, civicconnect_management
    TO civicconnect_app WITH INHERIT FALSE, SET TRUE;

-- Keep Supabase's public API roles out of this schema completely
REVOKE ALL ON SCHEMA civicconnect FROM PUBLIC;
REVOKE ALL ON SCHEMA civicconnect FROM anon, authenticated;

GRANT USAGE ON SCHEMA civicconnect
    TO civicconnect_app, civicconnect_client, civicconnect_staff, civicconnect_management;
-- No CREATE for civicconnect_app: Flyway runs as the schema owner (FLYWAY_USER), and objects the app role
-- created could be picked up by a SECURITY DEFINER function (V6, D-19).

-- ---------- civicconnect_app (before login) ----------
GRANT SELECT ON client, employee TO civicconnect_app;         -- login and /auth/me
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
-- Management may activate/deactivate clients but never read their personal data (ASR-005)
GRANT SELECT (client_id) ON client TO civicconnect_management;
GRANT UPDATE (active, updated_at) ON client TO civicconnect_management;


COMMIT;

-- =====================================================================
-- First manager (run once, separately, after the script above and before the first deploy)
--
-- Replace the name, the email (lower case) and the temporary password, then run ONLY this statement.
-- The password is hashed inside the database with BCrypt (pgcrypto), the same format Spring uses.
-- Do not save this query in the SQL editor, and change the password on the My account page straight
-- after the first login - then the temporary password is worthless.
-- If extensions.crypt does not exist: Database > Extensions > enable pgcrypto, then run it again.
-- =====================================================================
-- INSERT INTO civicconnect.employee (first_name, surname, email, password, role, active, created_at)
-- VALUES ('First', 'Manager', 'first.manager@example.com',
--         extensions.crypt('A-long-temporary-password', extensions.gen_salt('bf', 10)),
--         'MANAGEMENT', TRUE, now() AT TIME ZONE 'UTC');
--
-- Check it:  SELECT emp_id, email, role, active, left(password, 4) FROM civicconnect.employee;
--            (left(password, 4) should be $2a$)
-- =====================================================================
