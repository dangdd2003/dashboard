package com.usth.edu.vn.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates e-mail addresses. {@code null} is considered valid (combine with {@code @NotNull}/{@code @NotBlank}).
 * The compiled {@link Pattern} is immutable and thread-safe; matchers are created per call.
 */
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
      "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$");

  @Override
  public boolean isValid(String email, ConstraintValidatorContext constraintValidatorContext) {
    if (email == null) {
      return true;
    }
    return EMAIL_PATTERN.matcher(email).matches();
  }
}
