-- =====================================================================
-- CivicConnect - full database build for Supabase (PostgreSQL 17)
-- Run in the Supabase SQL editor as the default "postgres" user.
--
--  1. Schema exactly as Flyway V1 + V2 would build it (tables, rules, indexes,
--     audit trigger, append-only comment and audit tables)
--  2. Dummy data with a matching audit history, plus the default manager (admin@gmail.com)
--  3. Database roles for each type of user, with their permissions
--
-- WARNING: this DROPS the civicconnect schema and everything in it, then rebuilds it.
-- Only run it while the data is dummy data.
--
-- After running it, application.properties must have:
--   spring.flyway.baseline-on-migrate=true
--   spring.flyway.baseline-version=2
-- so Flyway treats this script as V1 + V2 and only runs V3 onwards.
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
-- 3. Dummy data (development only)
-- The audit triggers are switched off while loading, and a matching
-- history is written explicitly so request timelines look realistic.
-- Every dummy account's password is  Password123!  (BCrypt hash below)
-- =====================================================================
ALTER TABLE client           DISABLE TRIGGER trg_audit_client;
ALTER TABLE employee         DISABLE TRIGGER trg_audit_employee;
ALTER TABLE category         DISABLE TRIGGER trg_audit_category;
ALTER TABLE handler_category DISABLE TRIGGER trg_audit_handler_category;
ALTER TABLE request          DISABLE TRIGGER trg_audit_request;
ALTER TABLE request_handler  DISABLE TRIGGER trg_audit_request_handler;
ALTER TABLE comment          DISABLE TRIGGER trg_audit_comment;

INSERT INTO category (cat_id, cat_name, active) VALUES
    (1, 'Facility Faults',   TRUE),
    (2, 'Damaged Equipment', TRUE),
    (3, 'Security Concerns', TRUE),
    (4, 'IT Support',        TRUE),
    (5, 'Maintenance',       TRUE),
    (6, 'Lost Property',     TRUE),
    (7, 'Other',             TRUE),
    (8, 'Parking',           FALSE);   -- deactivated, but old requests still use it

-- 1 manager, 3 active staff, 1 deactivated staff member
INSERT INTO employee (emp_id, first_name, surname, email, password, phone, role, active, created_at, date_of_birth, street, area, city, postal_code) VALUES
    (1, 'Lerato', 'Nkosi',   'lerato.nkosi@civicconnect.example',  '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0110000001', 'MANAGEMENT', TRUE,  now() - interval '90 days', '1985-03-14', '12 Oak Street',   'Hatfield',   'Pretoria',     '0083'),
    (2, 'Johan',  'van Wyk', 'johan.vanwyk@civicconnect.example',  '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0110000002', 'STAFF',      TRUE,  now() - interval '90 days', '1990-07-02', NULL,              NULL,         'Pretoria',     NULL),
    (3, 'Ayesha', 'Patel',   'ayesha.patel@civicconnect.example',  '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0110000003', 'STAFF',      TRUE,  now() - interval '80 days', NULL,         NULL,              NULL,         NULL,           NULL),
    (4, 'Sipho',  'Dlamini', 'sipho.dlamini@civicconnect.example', '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0110000004', 'STAFF',      TRUE,  now() - interval '80 days', '1992-11-20', '5 Jacaranda Ave', 'Sunnyside',  'Pretoria',     '0002'),
    (5, 'Megan',  'Botha',   'megan.botha@civicconnect.example',   '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0110000005', 'STAFF',      FALSE, now() - interval '70 days', NULL,         NULL,              NULL,         NULL,           NULL);

-- Handler category scope (FR-005)
INSERT INTO handler_category (handler_category_id, emp_id, cat_id) VALUES
    (1, 2, 1), (2, 2, 5), (3, 2, 7),             -- Johan: Facility Faults, Maintenance, Other
    (4, 3, 2), (5, 3, 4),                        -- Ayesha: Damaged Equipment, IT Support
    (6, 4, 3), (7, 4, 6), (8, 4, 8),             -- Sipho: Security Concerns, Lost Property, Parking
    (9, 5, 1), (10, 5, 5);                       -- Megan (deactivated): Facility Faults, Maintenance

-- Clients (requesters); client 6 is deactivated
INSERT INTO client (client_id, first_name, surname, email, password, phone, active, created_at, updated_at, date_of_birth) VALUES
    (1, 'Thabo',  'Mokoena',   'thabo.mokoena@example.com',   '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0820000001', TRUE,  now() - interval '60 days', now() - interval '60 days', '1995-05-05'),
    (2, 'Naledi', 'Khumalo',   'naledi.khumalo@example.com',  '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0820000002', TRUE,  now() - interval '55 days', now() - interval '55 days', NULL),
    (3, 'Pieter', 'Smit',      'pieter.smit@example.com',     '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', NULL,         TRUE,  now() - interval '50 days', now() - interval '50 days', NULL),
    (4, 'Zanele', 'Mahlangu',  'zanele.mahlangu@example.com', '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0820000004', TRUE,  now() - interval '40 days', now() - interval '40 days', '1988-09-30'),
    (5, 'Ruan',   'Pretorius', 'ruan.pretorius@example.com',  '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', NULL,         TRUE,  now() - interval '30 days', now() - interval '30 days', NULL),
    (6, 'Fatima', 'Adams',     'fatima.adams@example.com',    '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.', '0820000006', FALSE, now() - interval '20 days', now() - interval '2 days',  NULL);

-- Requests: every status is represented; 3 are overdue (open for more than 7 days, CR-004)
INSERT INTO request (req_id, ref_num, cat_id, requester_id, description, location, attachment_url, status, created_at, updated_at, closed_at) VALUES
    (1,  'CC-2026-000001', 1, 1, 'Ceiling light in the main hall keeps flickering.',           'Community Hall, Main Hall', NULL,                                 'NEW',         now() - interval '1 day',   now() - interval '1 day',           NULL),
    (2,  'CC-2026-000002', 4, 2, 'Reception computer cannot connect to the printer.',          'Admin Office, Reception',   NULL,                                 'NEW',         now() - interval '9 days',  now() - interval '9 days',          NULL),
    (3,  'CC-2026-000003', 2, 3, 'Two chairs in the training room have broken legs.',          'Training Room 2',           'https://files.example.com/req3.jpg', 'ASSIGNED',    now() - interval '3 days',  now() - interval '2 days',          NULL),
    (4,  'CC-2026-000004', 3, 1, 'Side gate lock is broken and the gate stays open at night.', 'East Side Gate',            NULL,                                 'IN_PROGRESS', now() - interval '5 days',  now() - interval '3 days 2 hours',  NULL),
    (5,  'CC-2026-000005', 5, 4, 'Water leak under the kitchen sink.',                         'Staff Kitchen',             NULL,                                 'IN_PROGRESS', now() - interval '12 days', now() - interval '8 days',          NULL),
    (6,  'CC-2026-000006', 4, 5, 'Wi-Fi in the library drops every few minutes.',              'Library, Ground Floor',     NULL,                                 'RESOLVED',    now() - interval '6 days',  now() - interval '1 day',           NULL),
    (7,  'CC-2026-000007', 1, 2, 'Toilet in the ladies bathroom does not flush.',              'Ground Floor Bathrooms',    NULL,                                 'CLOSED',      now() - interval '20 days', now() - interval '14 days',         now() - interval '14 days'),
    (8,  'CC-2026-000008', 6, 6, 'Lost a black backpack near the sports field.',               'Sports Field',              NULL,                                 'REJECTED',    now() - interval '4 days',  now() - interval '3 days',          now() - interval '3 days'),
    (9,  'CC-2026-000009', 8, 3, 'Visitor parking bay lines have faded.',                      'Visitor Parking',           NULL,                                 'CLOSED',      now() - interval '40 days', now() - interval '33 days',         now() - interval '33 days'),
    (10, 'CC-2026-000010', 2, 4, 'Projector in the boardroom shows a cracked image.',          'Boardroom',                 NULL,                                 'ASSIGNED',    now() - interval '8 days',  now() - interval '7 days',          NULL),
    (11, 'CC-2026-000011', 7, 6, 'Request for an extra recycling bin at the entrance.',        'Main Entrance',             NULL,                                 'NEW',         now() - interval '2 hours', now() - interval '2 hours',         NULL),
    (12, 'CC-2026-000012', 3, 5, 'Security camera at the back door is not recording.',         'Back Door',                 NULL,                                 'RESOLVED',    now() - interval '15 days', now() - interval '9 days',          NULL);

-- Handler assignments. Request 5 was reassigned from Megan (5) to Johan (2) by the manager.
INSERT INTO request_handler (req_handler_id, req_id, emp_id, assigned_at, unassigned_at) VALUES
    (1, 3,  3, now() - interval '2 days',  NULL),
    (2, 4,  4, now() - interval '4 days',  NULL),
    (3, 5,  5, now() - interval '11 days', now() - interval '8 days'),
    (4, 5,  2, now() - interval '8 days',  NULL),
    (5, 6,  3, now() - interval '5 days',  NULL),
    (6, 7,  2, now() - interval '19 days', NULL),
    (7, 9,  4, now() - interval '39 days', NULL),
    (8, 10, 3, now() - interval '7 days',  NULL),
    (9, 12, 4, now() - interval '14 days', NULL);

-- Comments and resolution notes (FR-010); requesters only see visible_to_requester = true
INSERT INTO comment (com_id, req_id, emp_id, content, visible_to_requester, created_at) VALUES
    (1, 3,  1, 'Please prioritise - the training room is booked for Friday.',          FALSE, now() - interval '2 days 4 hours'),
    (2, 4,  4, 'Replacement lock ordered, gate chained in the meantime.',              TRUE,  now() - interval '3 days'),
    (3, 5,  2, 'Waiting for a plumber quote.',                                         FALSE, now() - interval '7 days'),
    (4, 6,  3, 'Router firmware updated and access point replaced.',                   TRUE,  now() - interval '1 day'),
    (5, 7,  2, 'Flush valve replaced.',                                                TRUE,  now() - interval '16 days'),
    (6, 8,  4, 'Duplicate of a lost property report already logged at reception.',     TRUE,  now() - interval '3 days'),
    (7, 9,  4, 'Bay lines repainted.',                                                 TRUE,  now() - interval '35 days'),
    (8, 12, 4, 'Camera power supply replaced; recording confirmed.',                   TRUE,  now() - interval '9 days');

-- ---------------------------------------------------------------------
-- Matching audit history (what the trigger would have written)
-- ---------------------------------------------------------------------
-- Accounts, categories and scope
INSERT INTO audit_entry (table_name, operation, record_id, new_data, emp_id, client_id, changed_at)
SELECT 'client', 'INSERT', client_id::text, ((to_jsonb(c) - 'password') || jsonb_build_object('active', TRUE, 'updated_at', created_at))::text, NULL::bigint, client_id, created_at FROM client c
UNION ALL
SELECT 'employee', 'INSERT', emp_id::text, ((to_jsonb(e) - 'password') || jsonb_build_object('active', TRUE))::text, CASE WHEN emp_id = 1 THEN NULL ELSE 1 END, NULL, created_at FROM employee e
UNION ALL
SELECT 'category', 'INSERT', cat_id::text, (to_jsonb(k) || jsonb_build_object('active', TRUE))::text, NULL, NULL, now() - interval '90 days' FROM category k
UNION ALL
SELECT 'handler_category', 'INSERT', handler_category_id::text, to_jsonb(h)::text, 1, NULL, now() - interval '60 days' FROM handler_category h;

-- Later account and category changes
INSERT INTO audit_entry (table_name, operation, record_id, old_data, new_data, emp_id, client_id, changed_at)
SELECT 'employee', 'UPDATE', '5', ((to_jsonb(e) - 'password') || '{"active": true}')::text, (to_jsonb(e) - 'password')::text, 1, NULL::bigint, now() - interval '8 days' FROM employee e WHERE emp_id = 5
UNION ALL
SELECT 'client', 'UPDATE', '6', ((to_jsonb(c) - 'password') || jsonb_build_object('active', TRUE, 'updated_at', c.created_at))::text, (to_jsonb(c) - 'password')::text, 1, NULL, c.updated_at FROM client c WHERE client_id = 6
UNION ALL
SELECT 'category', 'UPDATE', '8', (to_jsonb(k) || '{"active": true}')::text, to_jsonb(k)::text, 1, NULL, now() - interval '30 days' FROM category k WHERE cat_id = 8;

-- Request submissions (actor: the requester)
INSERT INTO audit_entry (table_name, operation, record_id, new_data, client_id, changed_at)
SELECT 'request', 'INSERT', req_id::text,
       (to_jsonb(r) || jsonb_build_object('status', 'NEW', 'updated_at', created_at, 'closed_at', NULL))::text,
       requester_id, created_at
FROM request r;

-- Status changes, step by step (from_status -> to_status, who, when)
CREATE TEMP TABLE seed_step (req_id bigint, from_status text, to_status text, emp_id bigint, at timestamp) ON COMMIT DROP;
INSERT INTO seed_step VALUES
    (3,  'NEW', 'ASSIGNED', 3, now() - interval '2 days'),
    (4,  'NEW', 'ASSIGNED', 4, now() - interval '4 days'),
    (4,  'ASSIGNED', 'IN_PROGRESS', 4, now() - interval '3 days 2 hours'),
    (5,  'NEW', 'ASSIGNED', 5, now() - interval '11 days'),
    (5,  'ASSIGNED', 'IN_PROGRESS', 5, now() - interval '10 days'),
    (5,  'IN_PROGRESS', 'IN_PROGRESS', 1, now() - interval '8 days'),   -- reassignment touches updated_at only
    (6,  'NEW', 'ASSIGNED', 3, now() - interval '5 days'),
    (6,  'ASSIGNED', 'IN_PROGRESS', 3, now() - interval '4 days'),
    (6,  'IN_PROGRESS', 'RESOLVED', 3, now() - interval '1 day'),
    (7,  'NEW', 'ASSIGNED', 2, now() - interval '19 days'),
    (7,  'ASSIGNED', 'IN_PROGRESS', 2, now() - interval '18 days'),
    (7,  'IN_PROGRESS', 'RESOLVED', 2, now() - interval '16 days'),
    (7,  'RESOLVED', 'CLOSED', 2, now() - interval '14 days'),
    (8,  'NEW', 'REJECTED', 4, now() - interval '3 days'),
    (9,  'NEW', 'ASSIGNED', 4, now() - interval '39 days'),
    (9,  'ASSIGNED', 'IN_PROGRESS', 4, now() - interval '37 days'),
    (9,  'IN_PROGRESS', 'RESOLVED', 4, now() - interval '35 days'),
    (9,  'RESOLVED', 'CLOSED', 4, now() - interval '33 days'),
    (10, 'NEW', 'ASSIGNED', 3, now() - interval '7 days'),
    (12, 'NEW', 'ASSIGNED', 4, now() - interval '14 days'),
    (12, 'ASSIGNED', 'IN_PROGRESS', 4, now() - interval '13 days'),
    (12, 'IN_PROGRESS', 'RESOLVED', 4, now() - interval '9 days');

INSERT INTO audit_entry (table_name, operation, record_id, old_data, new_data, emp_id, changed_at)
SELECT 'request', 'UPDATE', s.req_id::text,
       (to_jsonb(r) || jsonb_build_object('status', s.from_status,
            'updated_at', COALESCE(lag(s.at) OVER w, r.created_at), 'closed_at', NULL))::text,
       (to_jsonb(r) || jsonb_build_object('status', s.to_status, 'updated_at', s.at,
            'closed_at', CASE WHEN s.to_status IN ('CLOSED', 'REJECTED') THEN s.at END))::text,
       s.emp_id, s.at
FROM seed_step s JOIN request r USING (req_id)
WINDOW w AS (PARTITION BY s.req_id ORDER BY s.at);

-- Handler assignments and the one unassignment
INSERT INTO audit_entry (table_name, operation, record_id, new_data, emp_id, changed_at)
SELECT 'request_handler', 'INSERT', req_handler_id::text,
       (to_jsonb(h) || jsonb_build_object('unassigned_at', NULL))::text,
       CASE WHEN req_handler_id = 4 THEN 1 ELSE emp_id END, assigned_at
FROM request_handler h;

INSERT INTO audit_entry (table_name, operation, record_id, old_data, new_data, emp_id, changed_at)
SELECT 'request_handler', 'UPDATE', req_handler_id::text,
       (to_jsonb(h) || jsonb_build_object('unassigned_at', NULL))::text, to_jsonb(h)::text, 1, unassigned_at
FROM request_handler h WHERE unassigned_at IS NOT NULL;

-- Comments
INSERT INTO audit_entry (table_name, operation, record_id, new_data, emp_id, changed_at)
SELECT 'comment', 'INSERT', com_id::text, to_jsonb(c)::text, emp_id, created_at FROM comment c;

ALTER TABLE client           ENABLE TRIGGER trg_audit_client;
ALTER TABLE employee         ENABLE TRIGGER trg_audit_employee;
ALTER TABLE category         ENABLE TRIGGER trg_audit_category;
ALTER TABLE handler_category ENABLE TRIGGER trg_audit_handler_category;
ALTER TABLE request          ENABLE TRIGGER trg_audit_request;
ALTER TABLE request_handler  ENABLE TRIGGER trg_audit_request_handler;
ALTER TABLE comment          ENABLE TRIGGER trg_audit_comment;

-- Move the ID sequences past the explicit IDs used above
SELECT setval(pg_get_serial_sequence('category', 'cat_id'),                (SELECT max(cat_id) FROM category));
SELECT setval(pg_get_serial_sequence('employee', 'emp_id'),                (SELECT max(emp_id) FROM employee));
SELECT setval(pg_get_serial_sequence('client', 'client_id'),               (SELECT max(client_id) FROM client));
SELECT setval(pg_get_serial_sequence('request', 'req_id'),                 (SELECT max(req_id) FROM request));
SELECT setval(pg_get_serial_sequence('request_handler', 'req_handler_id'), (SELECT max(req_handler_id) FROM request_handler));
SELECT setval(pg_get_serial_sequence('comment', 'com_id'),                 (SELECT max(com_id) FROM comment));
SELECT setval(pg_get_serial_sequence('handler_category', 'handler_category_id'), (SELECT max(handler_category_id) FROM handler_category));
SELECT setval('request_ref_seq', (SELECT max(req_id) FROM request) + 1, false);

-- =====================================================================
-- 3b. Default manager account (DEC-008)
-- Always created, so a database without the dummy data still has a manager who can log in and
-- create the other accounts. Email admin@gmail.com, password  Password.123  (BCrypt hash below).
-- Change this password after the first login on any shared or production database.
-- The audit trigger is on, so this is recorded as a system change (no actor).
-- =====================================================================
INSERT INTO employee (first_name, surname, email, password, role, active, created_at)
VALUES ('System', 'Administrator', 'admin@gmail.com',
        '$2a$10$hqmOeFjEBUhyUPpcqexj7emtajHNx0qTETDazu5.0e8SEOGtgNMn6', 'MANAGEMENT', TRUE,
        now() AT TIME ZONE 'UTC');

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
GRANT CREATE ON SCHEMA civicconnect TO civicconnect_app;      -- Flyway's history table only

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
-- 5. Quick checks (optional - run after the script)
-- =====================================================================
-- Row counts:
-- SELECT 'request' AS t, count(*) FROM civicconnect.request UNION ALL
-- SELECT 'comment', count(*) FROM civicconnect.comment UNION ALL
-- SELECT 'audit_entry', count(*) FROM civicconnect.audit_entry;
--
-- Full history of request 7 (what GET /api/v1/requests/7/audit returns):
-- SELECT table_name, operation, record_id, emp_id, client_id, changed_at
-- FROM civicconnect.audit_entry
-- WHERE (table_name = 'request' AND record_id = '7')
--    OR (table_name IN ('comment', 'request_handler') AND coalesce(new_data, old_data)::jsonb ->> 'req_id' = '7')
-- ORDER BY changed_at;
--
-- The append-only rule should block this with an error:
-- UPDATE civicconnect.comment SET content = 'changed' WHERE com_id = 1;
