package com.usth.edu.vn.model.dto;

import java.util.Date;

import com.usth.edu.vn.model.UserDetails;
import com.usth.edu.vn.model.Users;

/**
 * Public view of a user. Never contains the password hash.
 * The canonical constructor is used by JPQL {@code SELECT NEW} projections in {@code UserRepository}.
 */
public record UserDto(
    Long id,
    String username,
    String role,
    String firstname,
    String lastname,
    String email,
    Date createTime) {

  public static UserDto from(Users user) {
    UserDetails detail = user.getUserDetail();
    return new UserDto(
        user.getId(),
        user.getUsername(),
        user.getRoles(),
        detail == null ? null : detail.getFirstname(),
        detail == null ? null : detail.getLastname(),
        detail == null ? null : detail.getEmail(),
        detail == null ? null : detail.getCreateTime());
  }
}
