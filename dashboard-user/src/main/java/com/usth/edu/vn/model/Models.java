package com.usth.edu.vn.model;

import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
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

  private String filepath;

  @Column(columnDefinition = "TEXT")
  private String description;

  private Date createTime;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  @EqualsAndHashCode.Exclude
  private Users user;

  @OneToMany(mappedBy = "model", cascade = CascadeType.PERSIST)
  @EqualsAndHashCode.Exclude
  private Set<Inferences> inference;

  @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
  @EqualsAndHashCode.Exclude
  private Set<Ratings> ratings;
}
