package com.civicconnect.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ARC-03 and D-30: what the single jar serves for each kind of path, without looking up unknown files. */
class SpaFallbackResolverTest {

    private final WebConfig.SpaFallbackResolver resolver =
            new WebConfig.SpaFallbackResolver(Set.of("index.html", "main-ABC.js", "favicon.ico"));

    @Test
    @DisplayName("Packaged files are served; Angular routes get index.html; /api and unknown files get nothing")
    void servesTheRightThing() throws Exception {
        Resource location = mock(Resource.class);
        Resource mainJs = mock(Resource.class);
        when(location.createRelative("main-ABC.js")).thenReturn(mainJs);

        assertThat(resolver.getResource("main-ABC.js", location)).isSameAs(mainJs);
        assertThat(resolver.getResource("staff/requests", location).getFilename()).isEqualTo("index.html");
        assertThat(resolver.getResource("api/v1/nothing", location)).isNull();
        assertThat(resolver.getResource("missing-file.js", location)).isNull();
    }

    @Test
    @DisplayName("Made-up paths are never looked up on the classpath, so they leave nothing in any cache (D-30)")
    void unknownPathsAreNotLookedUp() throws Exception {
        Resource location = mock(Resource.class);
        for (int i = 0; i < 1000; i++) {
            resolver.getResource("x" + i + "a".repeat(100), location);
            resolver.getResource("y" + i + ".js", location);
        }
        verify(location, never()).createRelative(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("The packaged files are listed from the classpath at start-up")
    void listsStaticFiles() {
        assertThat(WebConfig.StaticFiles.list()).contains("index.html");
    }
}
