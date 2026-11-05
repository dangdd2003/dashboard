package com.usth.edu.vn.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.usth.edu.vn.validation.ValidEmail;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
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
    @EqualsAndHashCode.Exclude
    private Users user;
}
