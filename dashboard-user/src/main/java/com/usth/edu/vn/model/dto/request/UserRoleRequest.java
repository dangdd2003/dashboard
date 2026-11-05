package com.usth.edu.vn.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Body of the admin-only {@code PUT /users/{id}/roles}: {@code {"role": "admin" | "user"}}. */
public record UserRoleRequest(
    @NotNull(message = "Role may not be null!")
    @Pattern(regexp = "admin|user", message = "Role must be 'admin' or 'user'!")
    String role) {
}
