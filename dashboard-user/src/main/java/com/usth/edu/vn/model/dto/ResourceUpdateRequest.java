package com.usth.edu.vn.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code PUT /resources/update}. Only the type can be changed; the stored
 * file path is server-managed and can never be set by a client.
 */
public record ResourceUpdateRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "type must match [A-Za-z0-9_-]{1,64}") String type) {
}
