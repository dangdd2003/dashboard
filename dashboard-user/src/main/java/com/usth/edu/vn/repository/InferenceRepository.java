package com.usth.edu.vn.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.usth.edu.vn.model.Inferences;
import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.model.Resources;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.InferenceDto;
import com.usth.edu.vn.services.ml.HttpErrors;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class InferenceRepository implements PanacheRepository<Inferences> {

  /** Resource type used for parameter files produced by a training run. */
  public static final String TRAINING_RESOURCE_TYPE = "ParamsFiles";

  private static final String SELECT_DTO = """
      SELECT NEW com.usth.edu.vn.model.dto.InferenceDto(
          i.id,
          i.result,
          u.id,
          ud.firstname,
          ud.lastname,
          m.id,
          r.id,
          i.createTime
      )
      FROM Inferences i
      LEFT JOIN Users u
      ON u.id = i.user.id
      LEFT JOIN UserDetails ud
      ON ud.id = u.userDetail.id
      LEFT JOIN Models m
      ON m.id = i.model.id
      LEFT JOIN Resources r
      ON r.id = i.resource.id
      """;

  public Optional<InferenceDto> findInferenceById(long id) {
    return getEntityManager().createQuery(SELECT_DTO + " WHERE i.id = :id", InferenceDto.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst();
  }

  public List<InferenceDto> findAllInferences() {
    return getEntityManager().createQuery(SELECT_DTO + " ORDER BY i.id", InferenceDto.class)
        .getResultList();
  }

  public List<InferenceDto> findAllInferencesByUserId(long userId) {
    return getEntityManager().createQuery(SELECT_DTO + " WHERE u.id = :id ORDER BY i.id", InferenceDto.class)
        .setParameter("id", userId)
        .getResultList();
  }

  public List<InferenceDto> findAllInferencesByModelIdAndUserId(long modelId, long userId) {
    return getEntityManager()
        .createQuery(SELECT_DTO + " WHERE m.id = :mid AND u.id = :uid ORDER BY i.id", InferenceDto.class)
        .setParameter("mid", modelId)
        .setParameter("uid", userId)
        .getResultList();
  }

  public List<InferenceDto> findAllInferencesByModelId(long modelId) {
    return getEntityManager().createQuery(SELECT_DTO + " WHERE m.id = :id ORDER BY i.id", InferenceDto.class)
        .setParameter("id", modelId)
        .getResultList();
  }

  /**
   * Persists an inference result in its own short transaction. Called after the
   * (slow) ML service call has returned, so no DB transaction spans the HTTP call.
   */
  @Transactional
  public Inferences recordInference(long userId, long modelId, String result) {
    EntityManager em = getEntityManager();
    Users user = em.find(Users.class, userId);
    if (user == null) {
      throw HttpErrors.notFound("User not found");
    }
    Models model = em.find(Models.class, modelId);
    if (model == null) {
      throw HttpErrors.notFound("Model not found");
    }
    Inferences inference = new Inferences();
    inference.setUser(user);
    inference.setModel(model);
    inference.setResource(null);
    inference.setResult(result);
    inference.setCreateTime(new Date());
    persist(inference);
    return inference;
  }

  /** Persists the parameters file produced by a training run, in its own short transaction. */
  @Transactional
  public Resources recordTrainingResult(long userId, String filepath) {
    EntityManager em = getEntityManager();
    Users user = em.find(Users.class, userId);
    if (user == null) {
      throw HttpErrors.notFound("User not found");
    }
    Resources resource = new Resources();
    resource.setType(TRAINING_RESOURCE_TYPE);
    resource.setFilepath(filepath);
    resource.setUser(user);
    resource.setCreateTime(new Date());
    em.persist(resource);
    return resource;
  }

  /** Deletes an inference; returns false when it does not exist. */
  @Transactional
  public boolean deleteInference(long id) {
    return deleteById(id);
  }
}
