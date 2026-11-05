package com.usth.edu.vn.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's rating of a model. A user can rate a given model at most once
 * (unique constraint on {@code (user_id, model_id)}).
 */
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ratings", schema = "user_management", uniqueConstraints = @UniqueConstraint(name = "uk_ratings_user_model", columnNames = {
    "user_id", "model_id" }))
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Ratings {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Min(1)
  @Max(5)
  private Integer stars;

  @Column(columnDefinition = "TEXT")
  private String comment;

  private Date createTime;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private Users user;

  @ManyToOne
  @JoinColumn(name = "model_id", referencedColumnName = "id")
  private Models model;
}
