# SEN381-Project-CivicConnect
Digital platform for logging, assigning and tracking CivicConnect's service requests. SEN381 team project.
# CivicConnect (Prototype)

A civic service request management system — citizens submit service requests (e.g. pothole repairs), which are categorized, tracked, and assigned to staff for resolution.

**Status:** Early prototype. Core backend (Spring Boot + PostgreSQL) and a minimal Angular frontend are working end-to-end for the `Category` entity. Other entities (Client, Employee, ServiceRequest, RequestHandler, AuditEntry) have database tables and JPA entities set up, but full CRUD APIs and frontend screens are not yet built for them.

## Tech Stack

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Hibernate, Flyway (DB migrations)
- **Database:** PostgreSQL
- **Frontend:** Angular (standalone components)
- **Build tools:** Maven (backend), npm (frontend)

## Project Structure

civicconnect-backend/ Spring Boot API
civicconnect-frontend/ Angular app
docs/ Project documentation

## Prerequisites

- JDK 21
- PostgreSQL (tested on 18.4)
- Node.js and npm
- Angular CLI: `npm install -g @angular/cli`

## Setup

### 1. Database

Create the database in PostgreSQL:
```sql
CREATE DATABASE civicconnect;
```

Tables are created automatically via Flyway migrations when the backend starts (see `civicconnect-backend/src/main/resources/db/migration/`).

### 2. Backend

cd civicconnect-backend

Copy `src/main/resources/application.properties.example` to `src/main/resources/application.properties` and fill in your own PostgreSQL username/password.

Build and run:
mvnw.cmd clean install -DskipTests

The API runs on `http://localhost:8080`.

### 3. Frontend

cd civicconnect-frontend
npm install --legacy-peer-deps
ng serve

> Note: plain `npm install` currently fails due to a known npm bug resolving Angular's peer dependencies. Use `--legacy-peer-deps`.

The app runs on `http://localhost:4200`.

## Known Limitations (Prototype)

- No authentication/authorization — all endpoints are open
- Passwords are stored as plain text (not hashed) — not production-safe
- No DTOs — API responses expose entity fields directly
- No input validation on the backend
- CORS is configured for `localhost:4200` only

## API Endpoints (so far)

| Method | Endpoint              | Description       |
|--------|------------------------|--------------------|
| GET    | `/api/categories`      | List all categories |
| GET    | `/api/categories/{id}` | Get one category    |
| POST   | `/api/categories`      | Create a category    |
| PUT    | `/api/categories/{id}` | Update a category    |
| DELETE | `/api/categories/{id}` | Delete a category    |