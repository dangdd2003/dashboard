package com.usth.edu.vn.model.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rating read model. Field order is the constructor order used by the
 * {@code SELECT NEW} queries in {@code RatingRepository} — keep them in sync.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingDto {

  private Long id;

  private Integer stars;

  private String comment;

  private Long user_id;

  private String username;

  private String firstname;

  private String lastname;

  private Long model_id;

  private Date createTime;
}
