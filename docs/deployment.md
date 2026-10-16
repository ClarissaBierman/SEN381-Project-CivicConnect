# Deploying CivicConnect (Render free plan + Supabase)

ARC-03 / C-07: one Docker image, one jar that serves the Angular app and the `/api` endpoints from the
same address. Render builds it from the `Dockerfile` and deploys the **prod** branch automatically, but
only after the commit's GitHub checks pass (CI and Security). The settings are in `render.yaml`.

```
feature branch --PR--> main --PR--> prod --GitHub checks pass--> Render builds the Dockerfile
                                                                     --> /api/v1/health is UP --> live
```

Two databases, two Supabase projects (the free plan allows two per organization):

| Project | Built with | Used by |
|---|---|---|
| The team's existing project (test) | `civicconnect_supabase_build.sql` (dummy data, default accounts) | Demos of the dummy data, manual testing |
| A new **production** project | `civicconnect_production_build.sql` (no dummy data, no default accounts) | The Render site (public) |

The dummy and default accounts' passwords are in the repository, so they must never exist in the
database behind the public site (D-31). Integration tests only run against a local or CI database (D-33).

## One-time setup

### 1. Release to prod

1. Merge `backend` into `main` through a pull request (two approvals, all checks green).
2. Create `prod` from `main` and push it:
   ```
   git checkout main
   git pull
   git checkout -b prod
   git push -u origin prod
   ```
3. Protect `prod` like `main` (GitHub > Settings > Branches > Add rule for `prod`: require a pull
   request and the status checks), so it only changes through reviewed pull requests from `main`.

### 2. Create the production database

1. Supabase > **New project** in your organization. The team's production project is in **West EU
   (London, eu-west-2)**, about 15 ms from Render's Frankfurt region. A project in South Africa (Cape
   Town) would keep the data in the country but add 150-200 ms to every query, because Render has no
   African region (see "Latency" below). Generate a strong database password and keep it in a password
   manager - it is the `FLYWAY_PASSWORD`.
2. Wait until the project is ready, open **SQL Editor > New query**, paste the whole of
   `civicconnect-backend/database/civicconnect_production_build.sql`, and replace
   `CHANGE_ME_to_a_long_random_password` with a new random password for `civicconnect_app` - this is
   the `DB_PASSWORD`. Run it. It ends with `COMMIT` and shows "Success". Only `civicconnect_app` can
   log in; the other three roles have no login, so they have no password to set. Never reuse a password
   that appears anywhere in the repository (such as the old default admin password): the pooler accepts
   logins from the whole internet. To change it later: `ALTER ROLE civicconnect_app PASSWORD '...';`
3. Create the first manager: in a **new** query, paste the `INSERT INTO civicconnect.employee ...`
   statement from the end of that file (without the `--`), fill in the name, a lower-case email and a
   temporary password, and run it. If it says `extensions.crypt` does not exist, enable **pgcrypto**
   under Database > Extensions and run it again. Do not save these queries.
4. Check: `SELECT emp_id, email, role, left(password, 4) FROM civicconnect.employee;` shows one
   manager whose password starts with `$2a$`.
5. Note the connection details: **Connect > Session pooler** shows the host (for London
   `aws-1-eu-west-2.pooler.supabase.com` - copy the exact one), port `5432`, database `postgres`, and a user name
   `postgres.<project-ref>` - the part after `postgres.` is the project ref.
   - Render can only reach IPv4 addresses and Supabase's direct connection is IPv6, so always use
     the **session pooler** (port 5432) - never `db.<project-ref>.supabase.co`, and never the
     transaction pooler (port 6543, which breaks Hibernate's prepared statements).
6. Pool size: the app opens up to 10 connections, plus one for Flyway at start-up, and during a
   deploy the old and new versions briefly run together (D-39). In the project's database settings,
   check the session pooler's pool size; if it is below about 22, ask the team whether to lower
   `spring.datasource.hikari.maximum-pool-size`.

### 3. Create the Render service

1. Render dashboard > **New > Blueprint**. Connect GitHub if asked. The repository belongs to
   ClarissaBierman, so the Render GitHub app has to be allowed on it - Clarissa may have to approve
   the request (GitHub > Settings > Applications > Render > Repository access).
2. Pick `SEN381-Project-CivicConnect` and the branch **prod**. Render reads `render.yaml` and shows
   one web service, `civicconnect` (free plan, Frankfurt, Docker). If the repository is not in the
   list, Render's GitHub app is not allowed on ClarissaBierman's account yet: Clarissa installs it
   (GitHub > Settings > Applications, or the "Configure GitHub" link Render shows) with access to this
   repository.
3. Fill in the values Render asks for:

   | Key | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://aws-1-eu-west-2.pooler.supabase.com:5432/postgres?sslmode=require` (your exact host from Connect; no user name or password in it) |
   | `DB_USERNAME` | `civicconnect_app.<project-ref>` |
   | `DB_PASSWORD` | the `civicconnect_app` password from step 2.2 |
   | `FLYWAY_USER` | `postgres.<project-ref>` |
   | `FLYWAY_PASSWORD` | the production project's database password |

   `JWT_SECRET` is generated by Render and `SERVER_FORWARD_HEADERS_STRATEGY` is already set. Never put
   these values in the repository, a commit message or a chat.
4. **Apply**. Render waits for the checks on the latest `prod` commit, then builds (several minutes:
   Angular, Maven, image). Follow it under the service's **Logs**. It is live when the log shows
   `Started CivicconnectBackendApplication` and the health check passes.
5. Open `https://<service>.onrender.com/api/v1/health` - it should show
   `{"database":"UP","status":"UP"}`. Then open the site, log in as the first manager, go to
   **My account** and change the temporary password, then add the staff accounts on the Staff page.

## After that

- **Releasing**: open a pull request from `main` to `prod`. When it is merged, the checks run on
  `prod` and Render deploys once they are all green. A failing check means Render keeps the previous
  version running.
- **Latency**: the app (Frankfurt) and the database (London) are close, so each database query adds
  roughly 10-20 ms - more than the local load test, which had no network between them (R-26). Repeat
  the k6 pilot against a test project in the same setup before quoting response times for the deployed
  site. (With a Cape Town database it would be 150-200 ms per query.)
- **Sleep**: the free instance stops after 15 minutes without visitors and takes a minute or more to
  start again. Open the site a few minutes before a demo.
- **Supabase pauses** a free project after a week without activity (`/api/v1/health` then answers 503).
  Restore it in the Supabase dashboard; the day before a demo, open the health URL (NFR-005 log).
- **Rollback**: in Render, open the service's **Events** and redeploy an earlier deploy, or revert the
  merge on `prod`. A jar from before V6 cannot show audit history on a V6 database (see the PED,
  Staging and rollback).
- **End-to-end tests against the site** need accounts that exist there; the dummy accounts the tests
  use only exist in the test project. Run them against a local or test build instead.
- **Load test**: never against the production project. Use the test project (or a local database).

## If something goes wrong

| Symptom | Cause and fix |
|---|---|
| Render never starts a deploy | A GitHub check failed (or did not run) on the latest `prod` commit. Fix it on `main` and release again. |
| `password authentication failed` | The user name needs the project ref: `civicconnect_app.<project-ref>` and `postgres.<project-ref>`. |
| `Tenant or user not found` | Wrong project ref or pooler region in `DB_URL` / user name - copy them from the Connect panel. |
| `DB_URL` copied as `postgresql://user:password@host...` | Use the JDBC form `jdbc:postgresql://host:5432/postgres?sslmode=require`; the user name and password go in their own variables (and `$` in a password must not be URL-encoded there). |
| `Network is unreachable` / an IPv6 address | `DB_URL` uses the direct host. Use the session pooler host. |
| `permission denied` during Flyway | `FLYWAY_USER` must be the owner, `postgres.<project-ref>`, not `civicconnect_app`. |
| Flyway: `relation "civicconnect.client" does not exist` | The production build script was not run (step 2.2). |
| `JWT_SECRET must be at least 32 characters` | `JWT_SECRET` was edited by hand; let Render generate it again. |
| Health check fails, log shows `OutOfMemoryError` | Check the log; the 512 MB plan may be too small for what was added. |
| Nobody can log in | No manager exists yet - step 2.3. |

Changing `JWT_SECRET` in Render logs everyone out (their tokens no longer verify) - the way to end all
sessions at once.
