package com.civicconnect.common.validation;

import com.civicconnect.access.dto.ClientPatch;
import com.civicconnect.access.dto.EmployeePatch;
import com.civicconnect.requests.dto.CategoryPatch;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** D-22: names sent in a PATCH must not be only spaces; names not sent are left alone. */
class NotBlankIfPresentValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalid(Object body) {
        return validator.validate(body).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Blank names are refused in client, employee and category changes; missing names are fine")
    void blankNamesRefused() {
        assertThat(invalid(new ClientPatch("   ", "\t", null, null, null, null)))
                .containsExactlyInAnyOrder("firstName", "surname");
        assertThat(invalid(new ClientPatch(null, null, null, null, null, null))).isEmpty();
        assertThat(invalid(new ClientPatch(" Thabo ", "Mokoena", null, null, null, null))).isEmpty();
        assertThat(invalid(new EmployeePatch("", " ", null, null, null, null, null, null, null, null, null, null)))
                .containsExactlyInAnyOrder("firstName", "surname");
        assertThat(invalid(new CategoryPatch("  ", null))).containsExactly("catName");
        // D-34: an empty email was accepted (@Email allows "") and locked the account out
        assertThat(invalid(new ClientPatch(null, null, "", null, null, null))).containsExactly("email");
        assertThat(invalid(new EmployeePatch(null, null, " ", null, null, null, null, null, null, null, null, null)))
                .contains("email");
        assertThat(invalid(new CategoryPatch(null, true))).isEmpty();
    }
}
