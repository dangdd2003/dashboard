package com.usth.edu.vn.model.dto.request;

import com.usth.edu.vn.validation.ValidEmail;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial profile update: {@code null} fields are left unchanged; present fields may not be blank. */
public record UserDetailUpdateRequest(
    @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "First name may not be blank!") String firstname,
    @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "Last name may not be blank!") String lastname,
    @Size(max = 255) @ValidEmail String email) {
}
