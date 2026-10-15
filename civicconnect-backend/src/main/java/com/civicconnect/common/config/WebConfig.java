package com.civicconnect.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ARC-03: one jar serves the Angular app and the API from the same origin.
 *  - The compiled Angular files are packaged into classpath:/static (see the Dockerfile).
 *  - Angular's own routes (/login, /staff/requests, ...) do not exist as files; any path that is not a file
 *    and not under /api/ gets index.html, so a refresh or a bookmarked link still opens the app.
 *  - CORS is only needed in local development, when ng serve on localhost:4200 calls the API directly.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String INDEX = "static/index.html";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${civicconnect.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                // No caching resolver: its cache is keyed by URL and never shrinks, so made-up URLs would
                // fill the memory (D-30). The resolver below never looks up a file that does not exist.
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver(StaticFiles.list()));
    }

    /**
     * Serves only the files that were packaged into static/ (listed once at start-up), index.html for an
     * Angular route, and nothing (404) for /api paths or other unknown files. Unknown paths are never looked
     * up on the classpath, so they leave nothing behind in any cache.
     */
    static class SpaFallbackResolver extends PathResourceResolver {

        private final Set<String> files;
        private final Resource index;

        SpaFallbackResolver(Set<String> files) {
            this.files = files;
            Resource candidate = new ClassPathResource(INDEX);
            this.index = candidate.exists() ? candidate : null;
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (files.contains(resourcePath)) {
                return location.createRelative(resourcePath);
            }
            if (resourcePath.startsWith("api/") || lastSegment(resourcePath).contains(".")) {
                return null;
            }
            return index;
        }

        private static String lastSegment(String path) {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }
    }

    /** The relative paths of the files under classpath:/static (works for a jar and for a folder). */
    static final class StaticFiles {

        private StaticFiles() {}

        static Set<String> list() {
            Set<String> files = new HashSet<>();
            try {
                for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:/static/**")) {
                    String url = r.getURL().toString();
                    int at = url.lastIndexOf("/static/");
                    if (at >= 0 && !url.endsWith("/") && r.isReadable()) {
                        files.add(url.substring(at + "/static/".length()));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot list the static files", e);
            }
            return Set.copyOf(files);
        }
    }
}
