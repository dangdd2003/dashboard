package com.usth.edu.vn.model;

import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// No @Data: Lombok equals/hashCode/toString over the bidirectional relationships
// recurse (StackOverflowError) and trigger lazy loading. Identity equality is used.
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "resources", schema = "user_management")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Resources {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  /** Path relative to the configured storage root (never absolute, never client-supplied). */
  private String filepath;

  private String type;

  private Date createTime;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private Users user;

  @OneToMany(mappedBy = "resource", cascade = CascadeType.PERSIST, orphanRemoval = true)
  private Set<Inferences> inference;
}
