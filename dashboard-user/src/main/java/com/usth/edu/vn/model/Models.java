package com.usth.edu.vn.model;

import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A trained model uploaded by a user.
 * <p>
 * Deleting a model also deletes its ratings and inferences (cascade REMOVE + orphanRemoval),
 * so neither a model delete nor a user delete fails on the inference/rating foreign keys.
 * <p>
 * No Lombok equals/hashCode/toString: they would traverse the bidirectional relationships.
 */
@Getter
@Setter
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "models", schema = "user_management")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Models {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String type;

  /** Path of the stored file, relative to the storage root. */
  private String filepath;

  @Column(columnDefinition = "TEXT")
  private String description;

  private Date createTime;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private Users user;

  @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Inferences> inference;

  @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Ratings> ratings;
}
