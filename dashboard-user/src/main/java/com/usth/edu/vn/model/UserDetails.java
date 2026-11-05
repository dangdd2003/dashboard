package com.usth.edu.vn.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.usth.edu.vn.validation.ValidEmail;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_details", schema = "user_management")
@JsonIdentityInfo(
    generator = ObjectIdGenerators.PropertyGenerator.class,
    property = "id"
)
public class UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "First name may not be blank!")
  private String firstname;

  @NotBlank(message = "Last name may not be blank!")
  private String lastname;

  @ValidEmail
  private String email;

  private Date createTime;

  @OneToOne
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private Users user;

  /** Identity based on the database id only (never on relationships). */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserDetails other)) {
      return false;
    }
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return UserDetails.class.hashCode();
  }

  @Override
  public String toString() {
    return "UserDetails{id=" + id + ", firstname='" + firstname + "', lastname='" + lastname
        + "', email='" + email + "', createTime=" + createTime + "}";
  }
}
