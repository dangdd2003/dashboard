package com.usth.edu.vn.model.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceDto {

  private Long id;

  /** Path relative to the storage root (never an absolute server path). */
  private String filepath;

  private String type;

  private Long user_id;

  private String firstname;

  private String lastname;

  private Date createTime;
}
