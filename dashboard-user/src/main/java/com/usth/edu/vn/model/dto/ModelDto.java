package com.usth.edu.vn.model.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model read model. Field order is the constructor order used by the
 * {@code SELECT NEW} query in {@code ModelRepository} — keep them in sync.
 * <p>
 * {@code stars} is the average rating (null when the model has no ratings),
 * {@code usageCount} the number of inferences run with the model.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelDto {

  private Long id;

  private String name;

  private String type;

  /** Stored file path, relative to the storage root. */
  private String filepath;

  private String description;

  private Double stars;

  private Long usageCount;

  private Long user_id;

  private String username;

  private String firstname;

  private String lastname;

  private Date createTime;
}
