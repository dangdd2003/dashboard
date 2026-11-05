package com.usth.edu.vn.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /users/update}: {@code {"userDetail": {"firstname", "lastname", "email"}}}.
 * Deliberately has no {@code roles}/{@code username}/{@code password}; roles are changed via the
 * admin-only {@code PUT /users/{id}/roles}. Unknown properties (e.g. a legacy {@code roles}) are ignored.
 */
public record UserUpdateRequest(
    @NotNull(message = "User detail may not be null!")
    @Valid
    UserDetailUpdateRequest userDetail) {
}
