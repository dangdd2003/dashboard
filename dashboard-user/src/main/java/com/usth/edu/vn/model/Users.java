package com.usth.edu.vn.model;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User account. The {@code password} column always holds a bcrypt (MCF) hash: callers must hash
 * the raw password (after validating it on the request DTO) before calling {@link #setPassword(String)}.
 * Never serialize this entity to clients; use {@link com.usth.edu.vn.model.dto.UserDto}.
 */
@Getter
@Setter
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users", schema = "user_management")
@JsonIdentityInfo(
    generator = ObjectIdGenerators.PropertyGenerator.class,
    property = "id"
)
@UserDefinition
public class Users {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Username
  @NotBlank(message = "Username may not be blank!")
  @Column(unique = true, nullable = false)
  private String username;

  /** bcrypt hash; never exposed in JSON. */
  @Password
  @JsonIgnore
  private String password;

  @Roles
  private String roles;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private UserDetails userDetail;

  @JsonIgnore
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Models> models;

  @JsonIgnore
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Resources> resources;

  @JsonIgnore
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Inferences> inferences;

  @JsonIgnore
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Ratings> ratings;

  /** Identity based on the database id only (never on relationships). */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Users other)) {
      return false;
    }
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return Users.class.hashCode();
  }

  @Override
  public String toString() {
    return "Users{id=" + id + ", username='" + username + "', roles='" + roles + "'}";
  }
}
