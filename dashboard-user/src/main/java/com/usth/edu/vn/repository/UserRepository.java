package com.usth.edu.vn.repository;

import com.usth.edu.vn.model.UserDetails;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.UserDto;
import com.usth.edu.vn.model.dto.request.UserCreateRequest;
import com.usth.edu.vn.model.dto.request.UserDetailRequest;
import com.usth.edu.vn.model.dto.request.UserDetailUpdateRequest;
import com.usth.edu.vn.model.dto.request.UserUpdateRequest;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class UserRepository implements PanacheRepository<Users> {

  public static final String ROLE_ADMIN = "admin";
  public static final String ROLE_USER = "user";
  public static final Set<String> ROLES = Set.of(ROLE_ADMIN, ROLE_USER);

  static final String USER_EXISTED_MESSAGE = "User is already existed!";

  /** Never selects the password hash. LEFT JOIN so users without a detail row are still listed. */
  private static final String SELECT_USER_DTO = """
      SELECT NEW com.usth.edu.vn.model.dto.UserDto(
          u.id,
          u.username,
          u.roles,
          ud.firstname,
          ud.lastname,
          ud.email,
          ud.createTime
      )
      FROM Users u
      LEFT JOIN u.userDetail ud
      """;

  public Optional<UserDto> findUserById(long id) {
    return getEntityManager()
        .createQuery(SELECT_USER_DTO + " WHERE u.id = :id", UserDto.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst();
  }

  public Optional<UserDto> findUserByUsername(String username) {
    return getEntityManager()
        .createQuery(SELECT_USER_DTO + " WHERE u.username = :username", UserDto.class)
        .setParameter("username", username)
        .getResultStream()
        .findFirst();
  }

  public List<UserDto> findAllUsers() {
    return getEntityManager()
        .createQuery(SELECT_USER_DTO + " ORDER BY u.id", UserDto.class)
        .getResultList();
  }

  /** @param pageNo 1-based page number; @param pageSize page size (>= 1). */
  public List<UserDto> findPagingUsers(int pageNo, int pageSize) {
    long offset = offset(pageNo, pageSize);
    if (offset > Integer.MAX_VALUE) {
      return List.of();
    }
    return getEntityManager()
        .createQuery(SELECT_USER_DTO + " ORDER BY u.id", UserDto.class)
        .setFirstResult((int) offset)
        .setMaxResults(pageSize)
        .getResultList();
  }

  /** Substring search over username, first/last name and email (LIKE wildcards in the keyword are literal). */
  public List<UserDto> findMatchedUsers(String keyword, int pageNo, int pageSize) {
    long offset = offset(pageNo, pageSize);
    if (offset > Integer.MAX_VALUE) {
      return List.of();
    }
    String pattern = "%" + escapeLike(keyword == null ? "" : keyword) + "%";
    return getEntityManager()
        .createQuery(SELECT_USER_DTO + """
             WHERE u.username LIKE :kw ESCAPE '!'
            OR ud.firstname LIKE :kw ESCAPE '!'
            OR ud.lastname LIKE :kw ESCAPE '!'
            OR ud.email LIKE :kw ESCAPE '!'
            ORDER BY u.id
            """, UserDto.class)
        .setParameter("kw", pattern)
        .setFirstResult((int) offset)
        .setMaxResults(pageSize)
        .getResultList();
  }

  public Optional<Users> findByUsername(String username) {
    return find("username", username).firstResultOptional();
  }

  public boolean existsByUsername(String username) {
    return count("username", username) > 0;
  }

  /**
   * Registers a new user with role {@code user}. The request must already be validated
   * ({@code @Valid}); the raw password is hashed here.
   *
   * @throws ClientErrorException 409 if the username is taken
   */
  public Users createUser(UserCreateRequest request) {
    if (existsByUsername(request.username())) {
      throw new ClientErrorException(USER_EXISTED_MESSAGE, 409);
    }
    UserDetailRequest detailRequest = request.userDetail();

    Users user = new Users();
    user.setUsername(request.username());
    user.setPassword(BcryptUtil.bcryptHash(request.password()));
    user.setRoles(ROLE_USER);

    UserDetails detail = new UserDetails();
    detail.setFirstname(detailRequest.firstname());
    detail.setLastname(detailRequest.lastname());
    detail.setEmail(detailRequest.email());
    detail.setCreateTime(new Date());
    detail.setUser(user);
    user.setUserDetail(detail);

    try {
      persist(user);
      flush();
    } catch (PersistenceException e) {
      if (isConstraintViolation(e)) {
        // concurrent registration with the same username hit the unique constraint
        throw new ClientErrorException(USER_EXISTED_MESSAGE, 409);
      }
      throw e;
    }
    return user;
  }

  private static boolean isConstraintViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
      if (t instanceof org.hibernate.exception.ConstraintViolationException
          || t instanceof SQLIntegrityConstraintViolationException) {
        return true;
      }
    }
    return false;
  }

  /** Applies the non-null profile fields of {@code request} to the (managed) {@code user}. */
  public Users updateDetails(Users user, UserUpdateRequest request) {
    UserDetailUpdateRequest changes = request.userDetail();
    if (changes == null) {
      return user;
    }
    UserDetails detail = user.getUserDetail();
    if (detail == null) {
      if (changes.firstname() == null || changes.lastname() == null) {
        throw new BadRequestException("First name and last name are required!");
      }
      detail = new UserDetails();
      detail.setCreateTime(new Date());
      detail.setUser(user);
      user.setUserDetail(detail);
      getEntityManager().persist(detail);
    }
    if (changes.firstname() != null) {
      detail.setFirstname(changes.firstname());
    }
    if (changes.lastname() != null) {
      detail.setLastname(changes.lastname());
    }
    if (changes.email() != null) {
      detail.setEmail(changes.email());
    }
    return user;
  }

  /** Sets the role of the (managed) {@code user}; {@code role} must be one of {@link #ROLES}. */
  public Users updateRole(Users user, String role) {
    if (role == null || !ROLES.contains(role)) {
      throw new BadRequestException("Role must be 'admin' or 'user'!");
    }
    user.setRoles(role);
    return user;
  }

  /**
   * Changes the password of the (managed) {@code user}. The new password must already be validated.
   *
   * @param verifyOldPassword when true, {@code oldPassword} must match the stored hash (else 400)
   */
  public void updatePassword(Users user, String oldPassword, String newPassword, boolean verifyOldPassword) {
    if (verifyOldPassword
        && (oldPassword == null || !BcryptUtil.matches(oldPassword, user.getPassword()))) {
      throw new BadRequestException("Password is not matched!");
    }
    user.setPassword(BcryptUtil.bcryptHash(newPassword));
  }

  private static long offset(int pageNo, int pageSize) {
    return (long) (Math.max(pageNo, 1) - 1) * Math.max(pageSize, 1);
  }

  private static String escapeLike(String value) {
    return value
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_");
  }
}
