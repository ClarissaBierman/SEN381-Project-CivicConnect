# syntax=docker/dockerfile:1
# ARC-03 single deployable: one image, one jar that serves the Angular app and the /api REST endpoints
# from the same origin. Built by Render on each deploy of prod, and by the CI "Deployment image" job on
# every push and pull request, so a broken build is caught before Render tries it.
# Secrets (DB_URL, DB_USERNAME, DB_PASSWORD, FLYWAY_USER, FLYWAY_PASSWORD, JWT_SECRET) are environment
# variables set on the host - nothing secret is copied into the image.

# ---------- 1. Angular production build ----------
FROM node:24-slim AS frontend
WORKDIR /build
ENV NG_CLI_ANALYTICS=false
COPY civicconnect-frontend/package.json civicconnect-frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY civicconnect-frontend/ ./
RUN npx ng build

# ---------- 2. Spring Boot jar with the Angular files on the classpath (static/) ----------
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY civicconnect-backend/ ./
COPY --from=frontend /build/dist/civicconnect-frontend/browser/ src/main/resources/static/
# Tests already ran in the CI Backend job; this stage only packages
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests package \
    && cp "$(find target -maxdepth 1 -name '*.jar' ! -name '*plain*' | head -1)" /build/app.jar

# ---------- 3. Runtime: Java runtime only, not root ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home civicconnect
COPY --from=backend /build/app.jar app.jar
USER civicconnect
# Sized for a 512 MB free instance with a fraction of a CPU (D-32). Java needs about 170 MB outside the
# heap (classes, metadata, compiled code, thread stacks), so the heap gets 55% (about 280 MB) and the
# other areas get upper limits, keeping the total under 512 MB so the container is never killed:
#  - the small serial collector, and only the fast JIT tier so start-up takes less CPU;
#  - at most 40 request threads (each needs a stack), plenty for a pilot with 20 users.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k" \
    SERVER_TOMCAT_THREADS_MAX=40
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
