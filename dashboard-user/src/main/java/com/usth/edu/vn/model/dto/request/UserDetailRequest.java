package com.usth.edu.vn.model.dto.request;

import com.usth.edu.vn.validation.ValidEmail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Profile data supplied when registering a user (all fields required). */
public record UserDetailRequest(
    @NotBlank(message = "First name may not be blank!") @Size(max = 255) String firstname,
    @NotBlank(message = "Last name may not be blank!") @Size(max = 255) String lastname,
    @NotBlank(message = "Email may not be blank!") @Size(max = 255) @ValidEmail String email) {
}
