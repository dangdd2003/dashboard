package com.usth.edu.vn.model.dto.request;

import com.usth.edu.vn.validation.ValidPassword;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /users/create}. The raw password is validated here and hashed by the
 * repository afterwards. Roles cannot be chosen by the client (always {@code user}).
 */
public record UserCreateRequest(
    @NotBlank(message = "Username may not be blank!")
    @Size(max = 255)
    @Pattern(regexp = "\\S+", message = "Username may not contain whitespace!")
    String username,

    @NotNull(message = "Password may not be null!")
    @ValidPassword
    String password,

    @NotNull(message = "User detail may not be null!")
    @Valid
    UserDetailRequest userDetail) {
}
