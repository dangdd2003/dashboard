package com.usth.edu.vn.services.ml;

import java.util.List;

/** JSON body sent to the ML service: {@code {"input": [1.0, 2.0, ...]}}. */
public record InferenceRequest(List<Double> input) {
}
