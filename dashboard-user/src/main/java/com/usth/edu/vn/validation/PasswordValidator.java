package com.usth.edu.vn.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Password policy: 8-255 characters, no whitespace, at least one digit, one lower-case letter,
 * one upper-case letter and one special character from {@code @#$%^&*()-+=}.
 * {@code null} is considered valid (combine with {@code @NotNull}/{@code @NotBlank}).
 * Must only be applied to raw (un-hashed) passwords on request DTOs.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

  // '-' is escaped so it is a literal character, not a range ")-+".
  private static final Pattern PASSWORD_PATTERN = Pattern.compile(
      "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&*()\\-+=])(?=\\S+$).{8,255}$");

  @Override
  public boolean isValid(String password, ConstraintValidatorContext constraintValidatorContext) {
    if (password == null) {
      return true;
    }
    return PASSWORD_PATTERN.matcher(password).matches();
  }
}
