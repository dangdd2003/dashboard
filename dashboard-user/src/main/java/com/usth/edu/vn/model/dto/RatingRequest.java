package com.usth.edu.vn.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /ratings/create} and {@code PUT /ratings/update}.
 * {@code stars} is required on create; on update, null fields are left unchanged.
 */
public record RatingRequest(
    @Min(1) @Max(5) Integer stars,
    @Size(max = 65535) String comment) {
}
