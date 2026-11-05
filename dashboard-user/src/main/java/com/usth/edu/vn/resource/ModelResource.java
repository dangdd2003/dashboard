package com.usth.edu.vn.resource;

import static com.usth.edu.vn.services.FileName.MODELS;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.ModelDto;
import com.usth.edu.vn.model.dto.ModelUpdateRequest;
import com.usth.edu.vn.repository.ModelRepository;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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

@Path("/models")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModelResource {

  @Inject
  ModelRepository modelRepository;

  @Inject
  FileServices fileServices;

  @Inject
  CurrentUser currentUser;

  @GET
  @Path("/")
  @PermitAll
  public Response getAllModels() {
    List<ModelDto> allModels = modelRepository.findAllModels();
    return Response.ok(allModels).build();
  }

  @GET
  @Path("{id}")
  @PermitAll
  public Response getModelById(@PathParam("id") long id) {
    return Response.ok(modelRepository.findModelById(id)).build();
  }

  @GET
  @Path("/paging")
  @PermitAll
  public Response getPagingModels(
      @QueryParam("pageNo") @DefaultValue("1") @Min(1) int pageNo,
      @QueryParam("pageSize") @DefaultValue("20") @Min(1) @Max(100) int pageSize) {
    return Response.ok(modelRepository.findPagingModels(pageNo, pageSize)).build();
  }

  @GET
  @Path("/search/{keyword}")
  @PermitAll
  public Response getSearchModels(@PathParam("keyword") String keyword) {
    return Response.ok(modelRepository.findMatchedModels(keyword)).build();
  }

  @GET
  @Path("/models-by-user")
  @PermitAll
  public Response getModelsByUser(@QueryParam("user_id") @NotNull Long userId) {
    return Response.ok(modelRepository.findModelsByUser(userId)).build();
  }

  @GET
  @Path("/top10-recently-use")
  @PermitAll
  public Response getTopTenRecentlyCreatedModels() {
    return Response.ok(modelRepository.findTopTenRecentlyCreatedModels()).build();
  }

  @GET
  @Path("/top10-most-use")
  @PermitAll
  public Response getTopTenMostUsedModels() {
    return Response.ok(modelRepository.findTopTenMostUsedModels()).build();
  }

  @GET
  @Path("/top10-best-rating")
  @PermitAll
  public Response getTopTenBestRatingModels() {
    return Response.ok(modelRepository.findTop10BestRatingModels()).build();
  }

  @GET
  @Path("/count")
  @PermitAll
  public Response getModelCount() {
    return Response.ok(modelRepository.count()).build();
  }

  /**
   * Uploads a model owned by the caller. Multipart form fields:
   * {@code model} (file), {@code name}, {@code type}, {@code description} (optional).
   */
  @POST
  @Path("/create")
  @RolesAllowed({ "admin", "user" })
  @Transactional
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response createModel(
      @RestForm("name") @NotBlank @Size(max = 255) String name,
      @RestForm("type") @NotNull @Pattern(regexp = ModelUpdateRequest.TYPE_REGEX, message = "must match "
          + ModelUpdateRequest.TYPE_REGEX) String type,
      @RestForm("description") @Size(max = 65535) String description,
      @RestForm("model") FileUpload file)
      throws IOException {
    if (file == null) {
      throw new BadRequestException("Missing file part 'model'");
    }
    Users owner = currentUser.user();
    String relativePath = fileServices.uploadFile(owner.getId(), file, MODELS, type);

    Models model = new Models();
    model.setName(name.strip());
    model.setType(type);
    model.setDescription(description);
    model.setFilepath(relativePath);
    model.setUser(owner);
    model.setCreateTime(new Date());
    modelRepository.persist(model);

    return Response.created(URI.create("/models/" + model.getId()))
        .entity(modelRepository.findModelById(model.getId()))
        .build();
  }

  /** Updates name/type/description of a model; owner or admin only. Returns the updated model. */
  @PUT
  @Path("/update")
  @RolesAllowed({ "admin", "user" })
  @Transactional
  public Response updateModel(@QueryParam("id") @NotNull Long id, @Valid @NotNull ModelUpdateRequest request) {
    Models model = modelRepository.findByIdOptional(id)
        .orElseThrow(() -> new NotFoundException("Model " + id + " not found"));
    currentUser.requireOwnerOrAdmin(model.getUser() == null ? null : model.getUser().getId());

    if (request.name() != null) {
      model.setName(request.name().strip());
    }
    if (request.type() != null) {
      model.setType(request.type());
    }
    if (request.description() != null) {
      model.setDescription(request.description());
    }
    return Response.ok(modelRepository.findModelById(id)).build();
  }

  /** Deletes a model with its ratings and inferences; the stored file is removed after commit. */
  @DELETE
  @Path("/delete/{id}")
  @RolesAllowed({ "admin", "user" })
  @Transactional
  @Produces(MediaType.TEXT_PLAIN)
  public Response deleteModel(@PathParam("id") long id) {
    Models model = modelRepository.findByIdOptional(id)
        .orElseThrow(() -> new NotFoundException("Model " + id + " not found"));
    currentUser.requireOwnerOrAdmin(model.getUser() == null ? null : model.getUser().getId());

    String filepath = model.getFilepath();
    modelRepository.deleteModelCascade(model);
    if (filepath != null && !filepath.isBlank()) {
      fileServices.deleteStoredFileAfterCommit(filepath);
    }
    return Response.ok("Model " + id + " is deleted!").build();
  }
}
