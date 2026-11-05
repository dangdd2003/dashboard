package com.usth.edu.vn.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A recorded inference run. Plain getters/setters only: no Lombok
 * equals/hashCode/toString over the bidirectional relationships.
 */
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "inferences", schema = "user_management")
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id"
)
public class Inferences {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Date createTime;

  /** Raw JSON returned by the ML service. */
  @Column(columnDefinition = "TEXT")
  private String result;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private Users user;

  @ManyToOne
  @JoinColumn(name = "model_id", referencedColumnName = "id")
  private Models model;

  @ManyToOne
  @JoinColumn(name = "resource_id", referencedColumnName = "id")
  private Resources resource;
}
