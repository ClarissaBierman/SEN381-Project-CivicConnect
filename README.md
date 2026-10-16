# SEN381-Project-CivicConnect

Digital platform for logging, assigning and tracking CivicConnect's service requests. SEN381 team project
(404 Team Not Found). Requesters submit and follow their requests, staff handle the requests in their
categories, and managers assign work, set priorities, manage categories and staff, and see activity reports
and the audit trail.

The Project Engineering Document (PED) in `docs/` is the controlled record of the requirements, decisions,
traceability (RTM), risks, defects and release evidence. This README only explains how to build and run the code.

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security with JWT bearer tokens, Data JPA, Bean Validation), Flyway |
| Database | PostgreSQL 17 (Supabase in the cloud, a local PostgreSQL or the CI service container) |
| Frontend | Angular 21 (standalone components), Vitest unit tests, Playwright end-to-end tests |
| Build | Maven wrapper (`mvnw`), npm |
| Delivery | GitHub Actions (CI and Security workflows), Docker image deployed to Render |

## Repository structure

```
civicconnect-backend/     Spring Boot API (modular monolith, ARC-02)
  src/main/java/com/civicconnect/
    access/               accounts, login, staff, clients, handler categories
    requests/             service requests, status workflow, comments, categories
    reporting/            activity report and audit trail
    common/               security, errors, rate limits, validation, configuration
  src/main/resources/db/migration/   Flyway migrations V1 to V7
  database/               scripts that build a test database (dummy data) or the production database
civicconnect-frontend/    Angular app; e2e/ holds the Playwright system tests
perf/                     k6 load test, seed data and results (see perf/README.md)
docs/                     PED and milestone documents, deployment.md, governance.md
.github/workflows/        ci.yml (build, tests, end-to-end, deployment image) and security.yml
Dockerfile, render.yaml   single deployable: one jar serves the Angular app and /api (ARC-03)
```

## Prerequisites

- JDK 21
- Node.js **22.22.2 or newer** (or 24.15+). Older Node 22 versions cannot run the frontend unit tests (jsdom 30).
- PostgreSQL 17 or later, locally or on Supabase

## Setup

### 1. Database

Build a test database with `civicconnect-backend/database/civicconnect_supabase_build.sql` (schema V1 and V2,
audit triggers, database roles, dummy data and the default manager). Run it as the schema owner, then give the
application's login role a password:

```sql
ALTER ROLE civicconnect_app LOGIN PASSWORD '<a password of your own>';
```

Flyway applies V3 and later automatically when the backend starts. The production database is built with
`civicconnect_production_build.sql` instead (no dummy or default accounts) - see `docs/deployment.md`.

### 2. Backend

```
cd civicconnect-backend
copy .env.example .env        (then fill in DB_URL, DB_USERNAME, DB_PASSWORD, FLYWAY_USER, FLYWAY_PASSWORD, JWT_SECRET)
mvnw.cmd spring-boot:run
```

`.env` is git-ignored: never commit it. The API runs on `http://localhost:8080`; `GET /api/v1/health` shows
whether it and the database are up.

### 3. Frontend

```
cd civicconnect-frontend
npm ci
npm start
```

The app runs on `http://localhost:4200` and sends `/api` calls to the backend through `proxy.conf.json`.
The dummy-data accounts are listed in `civicconnect-frontend/e2e/support.ts`.

## Tests

| Command (from the module folder) | What runs |
|---|---|
| `mvnw.cmd test` | Backend unit tests (no database needed) |
| `mvnw.cmd test -Pintegration` | Unit tests plus the integration tests against the database in `.env` (only allowed for a local database, D-33) |
| `mvnw.cmd verify -Pintegration,coverage` | As above, plus the JaCoCo coverage report in `target/site/jacoco` |
| `npx ng test --watch=false` | Frontend unit tests |
| `npm run e2e` | Playwright end-to-end journeys against a running backend and frontend (`E2E_BASE_URL` to test another address) |

The k6 load test is described in `perf/README.md`.

## CI and security checks

Every pull request and push to `main` and `prod` runs:

- **CI** - Backend (unit and integration tests on PostgreSQL 17, coverage), Frontend (build, unit tests,
  `npm audit` of runtime packages), End-to-end (Playwright on the integrated stack) and Deployment image
  (builds the Dockerfile and checks the app and API on one address with 512 MB of memory).
- **Security** - CodeQL static analysis (Java and TypeScript), gitleaks secret scanning and, on pull
  requests, dependency review. Dependabot opens weekly update pull requests.

A failing job blocks the merge into the protected `main` branch.

## Branches and releases

GitHub flow: a feature or fix branch per change, a pull request into `main` with two approvals from the other
team members (no self-approval). A release is a pull request from `main` into `prod`; Render deploys `prod`
once its checks pass. See `docs/governance.md` and `docs/deployment.md`.
