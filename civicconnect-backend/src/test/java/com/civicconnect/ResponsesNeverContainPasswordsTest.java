package com.civicconnect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ASR-005 / NFR-002: nothing the API sends back can carry a password or password hash.
 * Finds every record in the dto packages by scanning the classpath, so a new response DTO is checked
 * without anyone remembering to add it here. Records nested inside a DTO (lists, inner records) are
 * checked too. Only request bodies, which must accept a password, are left out.
 */
class ResponsesNeverContainPasswordsTest {

    /** Request bodies: these take a password from the user and are never sent back. */
    private static final Set<String> REQUEST_BODIES = Set.of(
            "RegisterRequest", "LoginRequest", "ClientPatch", "EmployeePatch", "NewEmployee", "PasswordChange");

    @Test
    @DisplayName("No response DTO (or record nested in one) has a password field")
    void noPasswordFields() throws Exception {
        List<Class<?>> responses = dtoRecords();
        assertThat(responses).as("the scan finds the response DTOs")
                .extracting(Class::getSimpleName)
                .contains("ClientResponse", "EmployeeResponse", "MeResponse", "AuthResponse", "RequestDetail",
                        "RequestSummary", "CommentResponse", "CategoryResponse", "AuditEntryResponse",
                        "ClientContactResponse", "RequestHistoryEntry", "ActivityReport", "RegistrationResponse",
                        "HandlerOption");

        for (Class<?> dto : responses) {
            checkNoPasswords(dto, new HashSet<>());
        }
    }

    private static void checkNoPasswords(Class<?> type, Set<Class<?>> seen) {
        if (!type.isRecord() || !seen.add(type)) return;
        for (RecordComponent c : type.getRecordComponents()) {
            assertThat(c.getName().toLowerCase()).as(type.getSimpleName() + "." + c.getName())
                    .doesNotContain("password");
            checkNoPasswords(c.getType(), seen);
            Type generic = c.getGenericType();
            if (generic instanceof ParameterizedType p) {
                for (Type arg : p.getActualTypeArguments()) {
                    if (arg instanceof Class<?> k) checkNoPasswords(k, seen);
                }
            }
        }
    }

    private static List<Class<?>> dtoRecords() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition d) {
                return true; // records are final classes, which the default rule would skip
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition d : scanner.findCandidateComponents("com.civicconnect")) {
            String name = d.getBeanClassName();
            if (name == null || !name.contains(".dto.")) continue;
            Class<?> type = Class.forName(name);
            if (!REQUEST_BODIES.contains(type.getSimpleName())) found.add(type);
        }
        return found;
    }
}
