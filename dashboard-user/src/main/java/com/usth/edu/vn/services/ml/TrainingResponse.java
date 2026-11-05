package com.usth.edu.vn.services.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of {@code GET /model/.../run}. {@code result} is the produced parameters file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingResponse(String message, String result) {
}
