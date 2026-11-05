package com.usth.edu.vn.repository;

import static com.usth.edu.vn.exception.ExceptionType.USER_NOT_FOUND;
import static com.usth.edu.vn.services.FileName.MODELS;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.usth.edu.vn.exception.CustomException;
import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.ModelDto;
import com.usth.edu.vn.services.FileServices;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ApplicationScoped
public class ModelRepository implements PanacheRepository<Models> {

  @Inject
  UserRepository userRepository;

  @Inject
  FileServices fileServices;

  @Inject
  EntityManager entityManager;

  private static final int PAGE_SIZE = 10;

  public Models getModelById(long id) {
    Models model = findById(id);
    return Models
        .builder()
        .name(model.getName())
        .type(model.getType())
        .user(userRepository.getUserById(model.getUser().getId()))
        .filepath(model.getFilepath())
        .description(model.getDescription())
        .createTime(model.getCreateTime())
        .build();
  }

  public ModelDto findModelById(long id) {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                  m.id,
                  m.name,
                  m.type,
                  m.filepath,
                  m.description,
                  AVG(r.stars),
                  COUNT(DISTINCT i.id),
                  m.user.id,
                  u.username,
                  ud.firstname,
                  ud.lastname,
                  m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            WHERE m.id = :id
            """, ModelDto.class)
        .setParameter("id", id)
        .getSingleResult();
  }

  public List<ModelDto> findModelsByUser(long user_id) {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                  m.id,
                  m.name,
                  m.type,
                  m.filepath,
                  m.description,
                  AVG(r.stars),
                  COUNT(DISTINCT i.id),
                  m.user.id,
                  u.username,
                  ud.firstname,
                  ud.lastname,
                  m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            WHERE u.id = :user_id
            GROUP BY m.id
            """, ModelDto.class)
        .setParameter("user_id", user_id)
        .getResultList();
  }

  public List<ModelDto> findAllModels() {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                m.id,
                m.name,
                m.type,
                m.filepath,
                m.description,
                AVG(r.stars),
                COUNT(DISTINCT i.id),
                m.user.id,
                u.username,
                ud.firstname,
                ud.lastname,
                m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            GROUP BY m.id
            """, ModelDto.class)
        .getResultList();
  }

  public List<ModelDto> findTopTenRecentlyCreatedModels() {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                  m.id,
                  m.name,
                  m.type,
                  m.filepath,
                  m.description,
                  AVG(r.stars),
                  COUNT(DISTINCT i.id),
                  m.user.id,
                  u.username,
                  ud.firstname,
                  ud.lastname,
                  m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            GROUP BY m.id
            ORDER BY m.createTime DESC
            LIMIT 10
            """, ModelDto.class)
        .getResultList();
  }

  public List<ModelDto> findTopTenMostUsedModels() {
    Query query = entityManager
        .createNativeQuery("""
            SELECT
                  m.id,
                  m.name,
                  m.type,
                  m.filepath,
                  m.description,
                  AVG(r.stars),
                  new_i.inference_count,
                  m.user_id,
                  u.username,
                  ud.firstname,
                  ud.lastname,
                  m.createTime
            FROM models m
            LEFT JOIN users u
            ON u.id = m.user_id
            LEFT JOIN user_details ud
            ON ud.user_id = u.id
            LEFT JOIN (
                SELECT
                    i.model_id,
                    COUNT(DISTINCT(i.id)) as inference_count
                FROM inferences i
                GROUP BY model_id
                ORDER BY COUNT(*) DESC)
                AS new_i
            ON new_i.model_id = m.id
            LEFT JOIN ratings r
            ON m.id = r.model_id
            GROUP BY m.id
            LIMIT 10
            """);
    List<Object[]> result = (List<Object[]>) query.getResultList();
    List<ModelDto> allModels = new ArrayList<>(result.size());
    for (Object[] o : result) {
      ModelDto modelDto = new ModelDto();
      modelDto.setId(Long.parseLong(o[0].toString()));
      modelDto.setName(o[1].toString());
      modelDto.setType(o[2].toString());
      modelDto.setFilepath(o[3].toString());
      modelDto.setDescription(o[4].toString());
      modelDto.setStars(o[5] == null ? null : Double.parseDouble(o[5].toString()));
      modelDto.setUsageCount(o[6] == null ? 0 : Long.parseLong(o[6].toString()));
      modelDto.setUser_id(Long.parseLong(o[7].toString()));
      modelDto.setUsername(o[8].toString());
      modelDto.setFirstname(o[9].toString());
      modelDto.setLastname(o[10].toString());
      // Hibernate ORM 7 returns java.time types for native query datetime columns
      modelDto.setCreateTime(o[11] instanceof LocalDateTime createTime ? Timestamp.valueOf(createTime) : (Date) o[11]);
      allModels.add(modelDto);
    }
    return allModels;
  }

  public List<ModelDto> findTop10BestRatingModels() {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                  m.id,
                  m.name,
                  m.type,
                  m.filepath,
                  m.description,
                  AVG(r.stars),
                  COUNT(DISTINCT i.id),
                  m.user.id,
                  u.username,
                  ud.firstname,
                  ud.lastname,
                  m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            GROUP BY m.id
            ORDER BY AVG(r.stars) DESC
            LIMIT 10
            """, ModelDto.class)
        .getResultList();
  }

  public List<ModelDto> findPagingModels(int pageNo, int pageSize) {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                  m.id,
                  m.name,
                  m.type,
                  m.filepath,
                  m.description,
                  AVG(r.stars),
                  COUNT(DISTINCT i.id),
                  m.user.id,
                  u.username,
                  ud.firstname,
                  ud.lastname,
                  m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            GROUP BY m.id
            """, ModelDto.class)
        .setFirstResult((pageNo - 1) * pageSize)
        .setMaxResults(pageSize)
        .getResultList();
  }

  public List<ModelDto> findMatchedModels(String keyword) {
    return entityManager
        .createQuery("""
            SELECT new com.usth.edu.vn.model.dto.ModelDto(
                m.id,
                m.name,
                m.type,
                m.filepath,
                m.description,
                AVG(r.stars),
                COUNT(DISTINCT i.id),
                m.user.id,
                u.username,
                ud.firstname,
                ud.lastname,
                m.createTime
            )
            FROM Models m
            LEFT JOIN Users u
            ON u.id = m.user.id
            LEFT JOIN UserDetails ud
            ON ud.id = u.userDetail.id
            LEFT JOIN Ratings r
            ON m.id = r.model.id
            LEFT JOIN Inferences i
            ON m.id = i.model.id
            WHERE m.name LIKE :name
            OR m.type LIKE :type
            OR m.description LIKE :description
            GROUP BY m.id
            """, ModelDto.class)
        .setParameter("name", "%" + keyword + "%")
        .setParameter("type", "%" + keyword + "%")
        .setParameter("description", "%" + keyword + "%")
        .getResultList();
  }

  public void addModel(long user_id, Models model, FileUpload modelFile) throws IOException, CustomException {
    Optional<Users> existedUser = userRepository.findByIdOptional(user_id);
    if (existedUser.isEmpty()) {
      throw new CustomException(USER_NOT_FOUND);
    }
    model.setFilepath(fileServices.uploadFile(user_id, modelFile, MODELS + File.separator + model.getType()));
    model.setUser(existedUser.get());
    model.setCreateTime(new Date());
    persist(model);
  }

  public void updateModel(long id, Models model) throws CustomException {
    Optional<Models> existedModel = findByIdOptional(id);
    if (existedModel.isEmpty()) {
      throw new CustomException("Model does not existed!");
    } else {
      Models saveModel = existedModel.get();
      if (model.getName() != null) {
        saveModel.setName(model.getName());
      }
      if (model.getType() != null) {
        saveModel.setType(model.getType());
      }
      if (model.getDescription() != null) {
        saveModel.setDescription(model.getDescription());
      }
    }
  }

  public void deleteModel(long id) {
    deleteById(id);
  }
}
