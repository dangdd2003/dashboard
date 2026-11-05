package com.usth.edu.vn.model.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /models/update?id=}. Null fields are left unchanged.
 * The stored file and owner cannot be changed through this request.
 */
public record ModelUpdateRequest(
    @Size(max = 255) @Pattern(regexp = NOT_BLANK_REGEX, message = "must not be blank") String name,
    @Pattern(regexp = TYPE_REGEX, message = "must match " + TYPE_REGEX) String type,
    @Size(max = 65535) String description) {

  /** Allowed model types (also used as a storage directory name). */
  public static final String TYPE_REGEX = "[A-Za-z0-9_-]{1,64}";

  static final String NOT_BLANK_REGEX = "(?s).*\\S.*";
}
