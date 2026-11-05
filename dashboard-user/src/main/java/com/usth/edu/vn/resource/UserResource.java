package com.usth.edu.vn.resource;

import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.UserDto;
import com.usth.edu.vn.model.dto.request.UserCreateRequest;
import com.usth.edu.vn.model.dto.request.UserPasswordChangeRequest;
import com.usth.edu.vn.model.dto.request.UserRoleRequest;
import com.usth.edu.vn.model.dto.request.UserUpdateRequest;
import com.usth.edu.vn.repository.UserRepository;
import com.usth.edu.vn.security.CurrentUser;
import com.usth.edu.vn.services.FileServices;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.CREATED;

@Path("/users")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

  private static final String USER_NOT_FOUND = "User not found!";

  @Inject
  UserRepository userRepository;

  @Inject
  FileServices fileServices;

  @Inject
  CurrentUser currentUser;

  @GET
  @Path("{id}")
  @RolesAllowed({ "admin", "user" })
  public UserDto getUserById(@PathParam("id") long id) {
    return userRepository.findUserById(id).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
  }

  @GET
  @RolesAllowed({ "admin", "user" })
  public List<UserDto> getAllUsers() {
    return userRepository.findAllUsers();
  }

  /** Empty page -> 200 []. */
  @GET
  @Path("/paging")
  @RolesAllowed({ "admin", "user" })
  public List<UserDto> getAllUsers(
      @QueryParam("pageNo") @DefaultValue("1") @Min(1) int pageNo,
      @QueryParam("pageSize") @DefaultValue("20") @Min(1) @Max(100) int pageSize) {
    return userRepository.findPagingUsers(pageNo, pageSize);
  }

  /** No match -> 200 []. */
  @GET
  @Path("/search")
  @RolesAllowed({ "admin", "user" })
  public List<UserDto> getSearchUsers(
      @QueryParam("keyword") @DefaultValue("") String keyword,
      @QueryParam("pageNo") @DefaultValue("1") @Min(1) int pageNo,
      @QueryParam("pageSize") @DefaultValue("20") @Min(1) @Max(100) int pageSize) {
    return userRepository.findMatchedUsers(keyword, pageNo, pageSize);
  }

  /** Profile of {@code username}, or of the caller when no username is given. */
  @GET
  @Path("/profile")
  @RolesAllowed({ "admin", "user" })
  public UserDto getProfile(@QueryParam("username") String username) {
    String target = username == null || username.isBlank() ? currentUser.username() : username;
    return userRepository.findUserByUsername(target).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
  }

  @GET
  @Path("/count")
  @RolesAllowed({ "admin", "user" })
  public long getUsersCount() {
    return userRepository.count();
  }

  /** Public registration. Role is always {@code user}. 201 + UserDto, 400 invalid body, 409 username taken. */
  @POST
  @Path("/create")
  @PermitAll
  @Transactional
  public Response createUser(@Valid @NotNull UserCreateRequest request) {
    Users user = userRepository.createUser(request);
    fileServices.createUserDir(user.getId());
    return Response.status(CREATED).entity(UserDto.from(user)).build();
  }

  /** Updates the caller's profile, or user {@code id}'s profile when the caller is an admin. */
  @PUT
  @Path("/update")
  @Transactional
  @RolesAllowed({ "admin", "user" })
  public UserDto updateUser(@QueryParam("id") Long id, @Valid @NotNull UserUpdateRequest request) {
    Users target = resolveTarget(id);
    userRepository.updateDetails(target, request);
    return UserDto.from(target);
  }

  /** Admin only: sets the role of user {@code id} to {@code admin} or {@code user}. */
  @PUT
  @Path("{id}/roles")
  @Transactional
  @RolesAllowed("admin")
  public UserDto updateRoles(@PathParam("id") long id, @Valid @NotNull UserRoleRequest request) {
    Users target = userRepository.findByIdOptional(id).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    userRepository.updateRole(target, request.role());
    return UserDto.from(target);
  }

  /**
   * Changes the caller's password (old password required), or, for an admin passing {@code id} of
   * another user, resets that user's password (old password not required). 204 on success.
   * Passwords are only accepted in the JSON body, never as query parameters.
   */
  @PUT
  @Path("/update-password")
  @Transactional
  @RolesAllowed({ "admin", "user" })
  public Response updatePassword(@QueryParam("id") Long id, @Valid @NotNull UserPasswordChangeRequest request) {
    long callerId = currentUser.id();
    boolean self = id == null || id == callerId;
    Users target = resolveTarget(id);
    userRepository.updatePassword(target, request.oldPassword(), request.newPassword(), self);
    return Response.noContent().build();
  }

  /** Deletes the caller's own account, or any account when the caller is an admin. 204 on success. */
  @DELETE
  @Path("/delete")
  @Transactional
  @RolesAllowed({ "admin", "user" })
  public Response deleteUser(@QueryParam("username") String username) {
    if (username == null || username.isBlank()) {
      throw new BadRequestException("Query parameter 'username' is required!");
    }
    if (!currentUser.isAdmin() && !username.equals(currentUser.username())) {
      throw new ForbiddenException("Not allowed to access another user's data");
    }
    Users user = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    return delete(user);
  }

  /** Deletes the caller's own account, or any account when the caller is an admin. 204 on success. */
  @DELETE
  @Path("/delete/{id}")
  @Transactional
  @RolesAllowed({ "admin", "user" })
  public Response deleteUser(@PathParam("id") long id) {
    currentUser.requireOwnerOrAdmin(id);
    Users user = userRepository.findByIdOptional(id).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    return delete(user);
  }

  private Response delete(Users user) {
    long userId = user.getId();
    userRepository.delete(user);
    // files are only removed once the DB deletion has committed
    fileServices.deleteUserDirAfterCommit(userId);
    return Response.noContent().build();
  }

  /**
   * The managed user entity to mutate: the caller when {@code id} is null (or the caller's own id),
   * otherwise user {@code id}, which requires the admin role (403 before 404, so existence is not leaked).
   */
  private Users resolveTarget(Long id) {
    long targetId = id == null ? currentUser.id() : id;
    currentUser.requireOwnerOrAdmin(targetId);
    return userRepository.findByIdOptional(targetId).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
  }
}
