package com.usth.edu.vn.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ratings", schema = "user_management")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Ratings {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Integer stars;

  @Column(columnDefinition = "TEXT")
  private String comment;

  private Date createTime;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  @EqualsAndHashCode.Exclude
  private Users user;

  @ManyToOne
  @JoinColumn(name = "model_id", referencedColumnName = "id")
  @EqualsAndHashCode.Exclude
  private Models model;
}