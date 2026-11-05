package com.usth.edu.vn.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.usth.edu.vn.model.Resources;
import com.usth.edu.vn.model.UserDetails;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.ResourceDto;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ResourceRepository implements PanacheRepository<Resources> {

  private static final String DTO_SELECT = """
      SELECT NEW com.usth.edu.vn.model.dto.ResourceDto(
          r.id,
          r.filepath,
          r.type,
          u.id,
          ud.firstname,
          ud.lastname,
          r.createTime
      )
      FROM Resources r
      JOIN r.user u
      LEFT JOIN u.userDetail ud
      """;

  @Inject
  UserRepository userRepository;

  @Inject
  EntityManager entityManager;

  public Optional<ResourceDto> findResourceById(long id) {
    return entityManager.createQuery(DTO_SELECT + " WHERE r.id = :id", ResourceDto.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst();
  }

  public List<ResourceDto> findResourceByUserId(long userId) {
    return entityManager.createQuery(DTO_SELECT + " WHERE u.id = :id ORDER BY r.id", ResourceDto.class)
        .setParameter("id", userId)
        .getResultList();
  }

  public List<ResourceDto> findAllResources() {
    return entityManager.createQuery(DTO_SELECT + " ORDER BY r.id", ResourceDto.class)
        .getResultList();
  }

  /** Persists a new resource row owned by {@code owner}; {@code filepath} must be a server-generated relative path. */
  public Resources createResource(Users owner, String type, String filepath) {
    Resources resource = new Resources();
    resource.setUser(owner);
    resource.setType(type);
    resource.setFilepath(filepath);
    resource.setCreateTime(new Date());
    persist(resource);
    return resource;
  }

  /**
   * Compatibility for server-side callers (e.g. InferenceService): persists the
   * given resource (type/filepath already set) for the user.
   */
  public void addResource(long userId, Resources resource) {
    Users user = userRepository.findByIdOptional(userId)
        .orElseThrow(() -> new NotFoundException("User not found"));
    resource.setUser(user);
    resource.setCreateTime(new Date());
    persist(resource);
  }

  public static ResourceDto toDto(Resources r) {
    Users u = r.getUser();
    UserDetails ud = u == null ? null : u.getUserDetail();
    return new ResourceDto(
        r.getId(),
        r.getFilepath(),
        r.getType(),
        u == null ? null : u.getId(),
        ud == null ? null : ud.getFirstname(),
        ud == null ? null : ud.getLastname(),
        r.getCreateTime());
  }
}
