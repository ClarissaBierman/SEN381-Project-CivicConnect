package com.civicconnect.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * D-33: the integration tests write to the database they are given - most roll back, but some commit (the
 * concurrency tests) and the audit trail is append-only, so their rows can never be removed. Before any
 * class tagged "integration" starts, this checks that the database is a local or CI one and stops the run
 * otherwise, so the tests can never touch the team's Supabase (staging/production) database by accident.
 *
 * Registered for every test class through JUnit's extension auto-detection (src/test/resources).
 * A throwaway remote database can be allowed on purpose with CIVICCONNECT_TEST_DB_ALLOWED=true.
 */
public class IntegrationDatabaseGuard implements BeforeAllCallback {

    static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "postgres", "host.docker.internal");

    @Override
    public void beforeAll(ExtensionContext context) {
        boolean integration = context.getTestClass()
                .map(c -> AnnotationSupport.findRepeatableAnnotations(c, Tag.class).stream()
                        .anyMatch(t -> t.value().equals("integration")))
                .orElse(false);
        if (!integration || "true".equalsIgnoreCase(setting("CIVICCONNECT_TEST_DB_ALLOWED").orElse(""))) {
            return;
        }
        String url = setting("spring.datasource.url").or(() -> setting("SPRING_DATASOURCE_URL"))
                .or(() -> setting("DB_URL")).or(() -> fromDotEnv("DB_URL")).orElse(null);
        if (url == null) {
            return; // Spring will fail with its own clear message
        }
        String host = host(url);
        if (!LOCAL_HOSTS.contains(host)) {
            throw new IllegalStateException("Integration tests refuse to run against " + host + ". They write test "
                    + "data, some of it permanent (append-only audit rows). Point DB_URL at a local or throwaway "
                    + "database built with database/civicconnect_supabase_build.sql, or set "
                    + "CIVICCONNECT_TEST_DB_ALLOWED=true if this database really is a throwaway one.");
        }
    }

    /** The host of a JDBC URL such as jdbc:postgresql://host:5432/db?sslmode=require (lower case, no brackets). */
    static String host(String jdbcUrl) {
        String rest = jdbcUrl.replaceFirst("^jdbc:[a-z]+://", "");
        String authority = rest.split("[/?]", 2)[0].split(",", 2)[0];
        if (authority.contains("@")) authority = authority.substring(authority.lastIndexOf('@') + 1);
        if (authority.startsWith("[")) return authority.substring(1, authority.indexOf(']')).toLowerCase(Locale.ROOT);
        int colon = authority.lastIndexOf(':');
        return (colon > 0 ? authority.substring(0, colon) : authority).toLowerCase(Locale.ROOT);
    }

    private static Optional<String> setting(String name) {
        String value = System.getProperty(name);
        if (value == null) value = System.getenv(name);
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    /** Spring reads .env from the working directory (spring.config.import=optional:file:.env[.properties]). */
    private static Optional<String> fromDotEnv(String key) {
        Path env = Path.of(".env");
        if (!Files.isRegularFile(env)) return Optional.empty();
        try {
            return Files.readAllLines(env).stream()
                    .map(String::trim)
                    .filter(l -> l.startsWith(key + "="))
                    .map(l -> l.substring(key.length() + 1).trim().replaceAll("^[\"']|[\"']$", ""))
                    .filter(v -> !v.isBlank())
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
