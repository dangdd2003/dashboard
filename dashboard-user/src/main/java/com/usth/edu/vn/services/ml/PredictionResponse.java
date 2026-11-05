package com.usth.edu.vn.services.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code POST /model/.../inference}. Unknown fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PredictionResponse(String message, Result result) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      @JsonProperty("N") Double n,
      @JsonProperty("P") Double p,
      @JsonProperty("K") Double k,
      Metrics metrics) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Metrics(Double mse, Double r2) {
  }
}
