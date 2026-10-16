-- =====================================================================
-- CivicConnect - load-test data (M3 performance exercise, perf/README.md)
-- Run as the database owner on a TEST or STAGING database that was built with
-- database/civicconnect_supabase_build.sql. NEVER run it on the live database.
-- Start the backend against that database once first: Flyway must have applied V3 and later
-- (this script uses the V4 priority column).
--
-- Adds:
--   * 20 staff accounts (perf.staff01..20@civicconnect.example, password  Password123!),
--     one per virtual user, each covering every category;
--   * 10,000 requests spread over the last 365 days, all statuses and priorities,
--     with handlers on the requests that have one.
-- Safe to run twice (it skips what already exists). The audit trigger stays on, so the
-- audit table grows the way it would in real use.
-- =====================================================================

BEGIN;
SET LOCAL search_path TO civicconnect;

INSERT INTO employee (first_name, surname, email, password, role, active, created_at)
SELECT 'Perf', 'Staff ' || lpad(g::text, 2, '0'),
       'perf.staff' || lpad(g::text, 2, '0') || '@civicconnect.example',
       '$2a$10$KNK7cFl353HR.JAUuW5b5u9.rLLv4dA7MsyUKTOcbeBWD5JKaFrm.',   -- Password123!
       'STAFF', TRUE, now() AT TIME ZONE 'UTC'
FROM generate_series(1, 20) g
ON CONFLICT (email) DO NOTHING;

INSERT INTO handler_category (emp_id, cat_id)
SELECT e.emp_id, c.cat_id
FROM employee e CROSS JOIN category c
WHERE e.email LIKE 'perf.staff%@civicconnect.example'
  AND NOT EXISTS (SELECT 1 FROM handler_category h WHERE h.emp_id = e.emp_id AND h.cat_id = c.cat_id);

-- Status mix per 10 requests: 2 New, 1 Assigned, 2 In Progress, 2 Resolved, 2 Closed, 1 Rejected.
-- Priority: a quarter each of not set, Low, Medium, High.
WITH clients AS (SELECT array_agg(client_id ORDER BY client_id) AS ids FROM client),
     cats    AS (SELECT array_agg(cat_id ORDER BY cat_id) AS ids FROM category),
     rows    AS (
        SELECT g,
               (ARRAY['NEW','NEW','ASSIGNED','IN_PROGRESS','IN_PROGRESS','RESOLVED','RESOLVED',
                      'CLOSED','CLOSED','REJECTED'])[1 + g % 10] AS status,
               (now() AT TIME ZONE 'UTC') - make_interval(days => g % 365, mins => (g * 37) % 1440) AS created
        FROM generate_series(1, 10000) g
        WHERE NOT EXISTS (SELECT 1 FROM request WHERE ref_num = 'CC-PERF-' || lpad(g::text, 6, '0')))
INSERT INTO request (ref_num, cat_id, description, location, status, requester_id, created_at, updated_at,
                     closed_at, priority)
SELECT 'CC-PERF-' || lpad(r.g::text, 6, '0'),
       cats.ids[1 + r.g % array_length(cats.ids, 1)],
       'Load-test request ' || r.g || ': ' || repeat('broken item reported by a member of the public. ', 4),
       'Building ' || (1 + r.g % 40) || ', room ' || (100 + r.g % 300),
       r.status,
       clients.ids[1 + r.g % array_length(clients.ids, 1)],
       r.created, r.created + interval '1 hour',
       CASE WHEN r.status IN ('CLOSED', 'REJECTED') THEN r.created + interval '2 days' END,
       (ARRAY[NULL, 'LOW', 'MEDIUM', 'HIGH'])[1 + r.g % 4]
FROM rows r, clients, cats;

-- A current handler on every load-test request past New (round-robin over the 20 perf staff)
WITH staff AS (SELECT array_agg(emp_id ORDER BY emp_id) AS ids FROM employee
               WHERE email LIKE 'perf.staff%@civicconnect.example')
INSERT INTO request_handler (req_id, emp_id, assigned_at)
SELECT r.req_id, staff.ids[1 + (r.req_id % array_length(staff.ids, 1))::int], r.created_at + interval '30 minutes'
FROM request r, staff
WHERE r.ref_num LIKE 'CC-PERF-%'
  AND r.status IN ('ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')
  AND NOT EXISTS (SELECT 1 FROM request_handler h WHERE h.req_id = r.req_id AND h.unassigned_at IS NULL);

COMMIT;

ANALYZE civicconnect.request;
ANALYZE civicconnect.request_handler;

-- Check: SELECT status, count(*) FROM civicconnect.request GROUP BY status ORDER BY 1;
