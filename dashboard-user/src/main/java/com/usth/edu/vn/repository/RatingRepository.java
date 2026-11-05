package com.usth.edu.vn.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.model.Ratings;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.RatingDto;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class RatingRepository implements PanacheRepository<Ratings> {

  @Inject
  EntityManager entityManager;

  /** Argument order must match the {@link RatingDto} constructor. */
  private static final String SELECT_RATING_DTO = """
      SELECT NEW com.usth.edu.vn.model.dto.RatingDto(
          r.id,
          r.stars,
          r.comment,
          u.id,
          u.username,
          ud.firstname,
          ud.lastname,
          m.id,
          r.createTime
      )
      FROM Ratings r
      JOIN r.user u
      LEFT JOIN u.userDetail ud
      JOIN r.model m
      """;

  public List<RatingDto> findAllRatings() {
    return entityManager.createQuery(SELECT_RATING_DTO + "ORDER BY r.id", RatingDto.class)
        .getResultList();
  }

  /** @throws NotFoundException (404) when no rating has this id */
  public RatingDto findRatingById(long id) {
    return entityManager.createQuery(SELECT_RATING_DTO + "WHERE r.id = :id", RatingDto.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Rating " + id + " not found"));
  }

  public List<RatingDto> findRatingByModelId(long modelId) {
    return entityManager.createQuery(SELECT_RATING_DTO + "WHERE m.id = :id ORDER BY r.id", RatingDto.class)
        .setParameter("id", modelId)
        .getResultList();
  }

  public List<RatingDto> findRatingByUserId(long userId) {
    return entityManager.createQuery(SELECT_RATING_DTO + "WHERE u.id = :id ORDER BY r.id", RatingDto.class)
        .setParameter("id", userId)
        .getResultList();
  }

  public Optional<Ratings> findByUserAndModel(long userId, long modelId) {
    return find("user.id = ?1 and model.id = ?2", userId, modelId).firstResultOptional();
  }

  /**
   * Persists a new rating of {@code model} by {@code user}.
   *
   * @throws ClientErrorException 409 if the user already rated the model
   *                              (pre-check, or the unique constraint on a race)
   */
  public Ratings addRating(Users user, Models model, int stars, String comment) {
    if (findByUserAndModel(user.getId(), model.getId()).isPresent()) {
      throw alreadyRated();
    }
    Ratings rating = new Ratings();
    rating.setStars(stars);
    rating.setComment(comment);
    rating.setUser(user);
    rating.setModel(model);
    rating.setCreateTime(new Date());
    try {
      persist(rating);
      flush();
    } catch (PersistenceException e) {
      if (isConstraintViolation(e)) {
        throw alreadyRated();
      }
      throw e;
    }
    return rating;
  }

  private static ClientErrorException alreadyRated() {
    return new ClientErrorException("Already rated this model", 409);
  }

  private static boolean isConstraintViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
