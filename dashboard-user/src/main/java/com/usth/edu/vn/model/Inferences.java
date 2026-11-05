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
@Table(name = "inferences", schema = "user_management")
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id"
)
public class Inferences {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  private Date createTime;

  private String result;

  @ManyToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  @EqualsAndHashCode.Exclude
  private Users user;

  @ManyToOne
  @JoinColumn(name = "model_id", referencedColumnName = "id")
  @EqualsAndHashCode.Exclude
  private Models model;

  @ManyToOne
  @JoinColumn(name = "resource_id", referencedColumnName = "id")
  @EqualsAndHashCode.Exclude
  private Resources resource;
}
