package com.civicconnect.common.validation;

import com.civicconnect.access.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaxBytesValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean passwordOk(String password) {
        return validator.validate(new RegisterRequest("A", "B", "a@b.co", null, password)).stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("Boundary: 72 bytes is accepted, 73 is not, counted in bytes rather than characters")
    void boundaryInBytes() {
        assertThat(passwordOk("a".repeat(72))).isTrue();
        assertThat(passwordOk("a".repeat(73))).isFalse();
        assertThat(passwordOk("é".repeat(36))).as("36 characters, 72 bytes").isTrue();
        assertThat(passwordOk("é".repeat(37))).as("37 characters, 74 bytes").isFalse();
        assertThat(passwordOk("é".repeat(60))).as("60 characters - passed @Size before, crashed BCrypt").isFalse();
    }
}
