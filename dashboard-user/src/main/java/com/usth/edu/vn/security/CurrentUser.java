package com.usth.edu.vn.security;

import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.repository.UserRepository;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

/**
 * Resolves the caller from the verified JWT (subject = username).
 * Use this instead of trusting user ids passed in query parameters.
 */
@RequestScoped
public class CurrentUser {

  @Inject
  SecurityIdentity identity;

  @Inject
  UserRepository userRepository;

  private Users cached;

  public boolean isAuthenticated() {
    return identity != null && !identity.isAnonymous();
  }

  public boolean isAdmin() {
    return isAuthenticated() && identity.hasRole("admin");
  }

  public String username() {
    if (!isAuthenticated()) {
      throw new NotAuthorizedException("Bearer");
    }
    return identity.getPrincipal().getName();
  }

  /** The caller's user entity; 401 if anonymous, 403 if the token's user no longer exists. */
  public Users user() {
    if (cached == null) {
      cached = userRepository.find("username", username()).firstResultOptional()
          .orElseThrow(() -> new ForbiddenException("User no longer exists"));
    }
    return cached;
  }

  public long id() {
    return user().getId();
  }

  /** Throws 403 unless the caller is the owner or an admin. */
  public void requireOwnerOrAdmin(Long ownerId) {
    if (isAdmin()) {
      return;
    }
    if (ownerId == null || ownerId.longValue() != id()) {
      throw new ForbiddenException("Not allowed to access another user's data");
    }
  }
}
