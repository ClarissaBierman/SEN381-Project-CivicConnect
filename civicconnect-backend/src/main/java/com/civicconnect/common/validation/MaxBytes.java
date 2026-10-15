package com.civicconnect.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits a string by its UTF-8 size in bytes, not its number of characters. BCrypt only accepts
 * passwords up to 72 bytes, and characters such as "é" take 2 bytes, so @Size(max = 72) alone lets
 * through passwords BCrypt rejects with a 500.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxBytesValidator.class)
public @interface MaxBytes {

    int value();

    String message() default "must be at most {value} bytes (some characters, such as accented letters, use 2 or more)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
