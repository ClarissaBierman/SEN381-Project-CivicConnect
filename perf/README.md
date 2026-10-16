# Performance / load test (M3)

One small, justified load test on the most performance-sensitive operation: **staff filtering the
request list** (`GET /api/v1/requests`, API-07).

## Why this operation

- It is the screen handlers and managers use all day (FR-005, FR-006), so it is the most frequent read.
- It is the most expensive read: filters on status, category and dates, a sort, paging in the
  database, the per-request account check (B1) and a second query for handler names.
- The PED sets a measurable target for it.

| Source | Target | How it is checked |
|---|---|---|
| FR-006 / ASR-004 | Filter/sort results in **2 s or less** | k6 threshold `p(95) < 2000 ms` on the list call |
| NFR-001 | 95th percentile **≤ 3 s at ≤ 20 concurrent users** | Covered by the stricter 2 s threshold, at 20 users |
| (team choice) | Less than 1 % failed calls | k6 threshold `http_req_failed < 1 %` |

20 users is the pilot load in assumption A-01.

## Files

| File | What it is |
|---|---|
| `seed-perf-data.sql` | Test data: 20 staff accounts (one per virtual user) and 10,000 requests over 365 days |
| `request-list.k6.js` | The k6 script: workload, thresholds and summary |
| `results/request-list-*.json` | Full k6 output of each run (all metrics) |

## How to run

1. Build a **test** database with `civicconnect-backend/database/civicconnect_supabase_build.sql`
   (never the live database).
2. Start the backend against it, so Flyway applies V3 to V5 (the seed needs the V4 `priority` column).
   Raise the **per-IP** limits for the run only (see Limitations):
   ```
   java -Dcivicconnect.rate-limit.ip.requests-per-minute=100000 \
        -Dcivicconnect.rate-limit.auth.per-ip-per-minute=1000 -jar civicconnect-backend/target/<jar>
   ```
3. With the backend running, load `perf/seed-perf-data.sql` as the database owner.
4. Install [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) and run from the repository root:
   ```
   k6 run -e BASE_URL=http://localhost:8080 perf/request-list.k6.js
   ```
   The run fails (exit code 99) if a threshold is missed.

## Workload

- 20 virtual users, each logged in as their own staff account, covering every category.
- Each user repeatedly opens the request list with a random mix of filters like the queue screen:
  status (60 % of calls), category (50 %), a "from" date 7/30/90 days back (40 %), a "to" date (20 %),
  sort newest / oldest / priority, page 0 (80 %) or pages 1–3, 20 rows per page.
- Think time 2–5 s between calls (a person reading the list).
- 30 s ramp-up to 20 users, 3 minutes at 20 users, 15 s ramp-down.

## Results

The test was run on two machines. The team's run (Run A) is the evidence of record; Run B, on a
different machine and operating system, confirms the result is repeatable.

| | Run A - team laptop (15/10/2026, 11:35 UTC) | Run B - cloud container (14/10/2026, 22:10 UTC) |
|---|---|---|
| Machine | Intel Core i7-1255U (10 cores), 16 GB RAM | 2 vCPU, 8 GB RAM |
| Operating system | Windows 11 Home | Linux |
| Software | PostgreSQL 18, k6 v2.2.0, backend via `mvnw spring-boot:run` | PostgreSQL 17.9, k6 v1.3.0, OpenJDK 21.0.12 |
| Data | build script dummy data + `seed-perf-data.sql` (about 10,000 requests) | same (10,021 requests, 7,014 assignments, 17,307 audit rows) |

In both runs the backend, the database and k6 ran on the same machine. Database connection pool: 10 in
Run A (the committed setting, `spring.datasource.hikari.maximum-pool-size=10`) and 5 in Run B (a copy
taken before the setting went back to 10).

**Pilot workload (pass/fail test)**

| Metric | Run A | Run B | Target | |
|---|---|---|---|---|
| List calls | 1,166 | 1,157 | | |
| Median | 16.7 ms | 14.3 ms | | |
| p90 / p95 / p99 | 26.2 / **29.8** / 42.6 ms | 26.1 / 31.6 / 42.6 ms | p95 ≤ 2,000 ms | PASS |
| Max | 149.9 ms | 156.8 ms | | |
| Failed calls | 0.00 % | 0.00 % | < 1 % | PASS |
| 429 (rate limited) | 0 | 0 | | |
| Throughput | 5.19 requests/s | 5.1 requests/s | | |

Files: `results/request-list-2026-10-15T11-35-43-185Z.json` (A),
`results/request-list-2026-10-14T22-14-00-960Z.json` (B).

**Exploratory headroom probe** (`MODE=stress`, not a pass/fail test): 50 users with no think time
for 1 minute, all rate limits raised.

| Metric | Run A | Run B |
|---|---|---|
| List calls | 16,098 | 11,789 |
| Throughput | 259 requests/s (about 50 times the pilot rate) | 189 requests/s |
| Median / p95 / p99 | 174 / 316 / 443 ms | 229 / 597 / 881 ms |
| Max | 839 ms | 1,885 ms |
| Failed calls | 0.00 % | 0.00 % |

Files: `results/request-list-stress-2026-10-15T11-39-35-250Z.json` (A),
`results/request-list-stress-2026-10-14T22-16-07-593Z.json` (B).

## Interpretation

- **At the pilot load the target is met by a wide margin**: p95 29.8 ms against a 2,000 ms target,
  with no errors, and the second machine gives almost the same figures (31.6 ms). The paging in the
  database and the `(cat_id, status, created_at)` index (ASR-004) keep each call small even with
  10,000 requests.
- **Headroom**: at about 50 times the pilot request rate, p95 is still about 0.3 s with no errors.
  Response times grow under that load because requests wait for a free database connection (10 in
  Run A, 5 in Run B) and, on the smaller machine, for CPU. Nothing failed, so the
  first limit is queueing, not errors.
- **Rate limiting** did not interfere with the pilot workload: each user stays far below the
  per-account limit (120 calls/minute).

## What this does not prove (limitations)

- **Network latency is missing.** The database was on the same machine. Supabase is reached over the
  internet (tens of milliseconds per query), and each list call makes several queries. Times on
  Supabase will be higher; the run should be repeated against the staging deployment.
- **Shared IP.** All virtual users came from one IP address, so the per-IP limits (300 calls and
  20 logins per minute) were raised for the run. Real users have their own addresses. The
  per-account limits stayed at their production values.
- **Reads only.** No writes ran at the same time (submissions, status changes and the audit trigger
  add row locks and inserts).
- **Synthetic data.** The data is spread evenly over categories and dates; real data will be uneven.
- **One instance.** A single backend instance was tested, as planned (A-01). Running more than one
  instance would also need the in-memory rate-limit counters moved to a shared store.
- **Not the browser.** Only the API was measured; the time for Angular to draw the page is not included.
