package com.usth.edu.vn.repository;

import java.util.List;

import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.model.dto.ModelDto;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ModelRepository implements PanacheRepository<Models> {

  @Inject
  EntityManager entityManager;

  private static final int TOP_N = 10;

  /**
   * Average stars and usage count are computed with correlated subqueries, so
   * there is no outer aggregation (no GROUP BY needed, no ONLY_FULL_GROUP_BY
   * error) and no Ratings x Inferences row blow-up.
   * Argument order must match the {@link ModelDto} constructor.
   */
  private static final String SELECT_MODEL_DTO = """
      SELECT new com.usth.edu.vn.model.dto.ModelDto(
          m.id,
          m.name,
          m.type,
          m.filepath,
          m.description,
          (SELECT AVG(r.stars) FROM Ratings r WHERE r.model = m),
          (SELECT COUNT(i.id) FROM Inferences i WHERE i.model = m),
          u.id,
          u.username,
          ud.firstname,
          ud.lastname,
          m.createTime
      )
      FROM Models m
      LEFT JOIN m.user u
      LEFT JOIN u.userDetail ud
      """;

  private static final String AVG_STARS = "(SELECT AVG(r2.stars) FROM Ratings r2 WHERE r2.model = m)";

  private static final String USAGE_COUNT = "(SELECT COUNT(i2.id) FROM Inferences i2 WHERE i2.model = m)";

  private List<ModelDto> list(String suffix) {
    return entityManager.createQuery(SELECT_MODEL_DTO + suffix, ModelDto.class).getResultList();
  }

  /** @throws NotFoundException (404) when no model has this id */
  public ModelDto findModelById(long id) {
    return entityManager.createQuery(SELECT_MODEL_DTO + "WHERE m.id = :id", ModelDto.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Model " + id + " not found"));
  }

  public List<ModelDto> findModelsByUser(long userId) {
    return entityManager.createQuery(SELECT_MODEL_DTO + "WHERE u.id = :userId ORDER BY m.id", ModelDto.class)
        .setParameter("userId", userId)
        .getResultList();
  }

  public List<ModelDto> findAllModels() {
    return list("ORDER BY m.id");
  }

  public List<ModelDto> findTopTenRecentlyCreatedModels() {
    return entityManager.createQuery(SELECT_MODEL_DTO + "ORDER BY m.createTime DESC, m.id DESC", ModelDto.class)
        .setMaxResults(TOP_N)
        .getResultList();
  }

  public List<ModelDto> findTopTenMostUsedModels() {
    return entityManager.createQuery(SELECT_MODEL_DTO + "ORDER BY " + USAGE_COUNT + " DESC, m.id", ModelDto.class)
        .setMaxResults(TOP_N)
        .getResultList();
  }

  /** Unrated models sort last. */
  public List<ModelDto> findTop10BestRatingModels() {
    return entityManager
        .createQuery(SELECT_MODEL_DTO + "ORDER BY COALESCE(" + AVG_STARS + ", 0.0) DESC, m.id", ModelDto.class)
        .setMaxResults(TOP_N)
        .getResultList();
  }

  /** @param pageNo 1-based page number (validated >= 1 by the resource) */
  public List<ModelDto> findPagingModels(int pageNo, int pageSize) {
    return entityManager.createQuery(SELECT_MODEL_DTO + "ORDER BY m.id", ModelDto.class)
        .setFirstResult((pageNo - 1) * pageSize)
        .setMaxResults(pageSize)
        .getResultList();
  }

  public List<ModelDto> findMatchedModels(String keyword) {
    return entityManager.createQuery(SELECT_MODEL_DTO + """
        WHERE m.name LIKE :kw
        OR m.type LIKE :kw
        OR m.description LIKE :kw
        ORDER BY m.id
        """, ModelDto.class)
        .setParameter("kw", "%" + keyword + "%")
        .getResultList();
  }

  /**
   * Deletes the model together with its ratings and inferences.
   * The children are bulk-deleted first so the model row can be removed without
   * FK violations and without loading every child entity.
   */
  public void deleteModelCascade(Models model) {
    entityManager.createQuery("DELETE FROM Ratings r WHERE r.model = :model")
        .setParameter("model", model)
        .executeUpdate();
    entityManager.createQuery("DELETE FROM Inferences i WHERE i.model = :model")
        .setParameter("model", model)
        .executeUpdate();
    delete(model);
  }
}
