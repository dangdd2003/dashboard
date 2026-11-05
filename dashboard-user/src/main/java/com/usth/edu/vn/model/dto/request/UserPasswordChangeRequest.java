package com.usth.edu.vn.model.dto.request;

import com.usth.edu.vn.validation.ValidPassword;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /users/update-password}. {@code oldPassword} is required when changing one's own
 * password; it is ignored when an admin resets another user's password.
 */
public record UserPasswordChangeRequest(
    String oldPassword,

    @NotNull(message = "New password may not be null!")
    @ValidPassword
    String newPassword) {
}
