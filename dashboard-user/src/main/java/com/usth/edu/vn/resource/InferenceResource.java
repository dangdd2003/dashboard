package com.usth.edu.vn.resource;

import java.util.List;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.usth.edu.vn.model.dto.InferenceDto;
import com.usth.edu.vn.repository.InferenceRepository;
import com.usth.edu.vn.security.CurrentUser;
import com.usth.edu.vn.services.InferenceService;
import com.usth.edu.vn.services.ml.HttpErrors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Inference endpoints. The caller is always taken from the JWT ({@link CurrentUser});
 * a {@code user_id} sent by clients is ignored for writes.
 *
 * No method here is {@code @Transactional}: the ML calls must not run inside a DB
 * transaction, and persistence happens in short repository transactions.
 */
@Path("/inferences")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class InferenceResource {

  @Inject
  InferenceRepository inferenceRepository;

  @Inject
  InferenceService inferenceService;

  @Inject
  CurrentUser currentUser;

  @GET
  @RolesAllowed("admin")
  public Response getAllInferences() {
    List<InferenceDto> allInferences = inferenceRepository.findAllInferences();
    return Response.ok(allInferences).build();
  }

  @GET
  @Path("/{id}")
  @RolesAllowed({ "admin", "user" })
  public Response getInferenceById(@PathParam("id") long id) {
    InferenceDto inference = inferenceRepository.findInferenceById(id)
        .orElseThrow(() -> HttpErrors.notFound("Inference not found"));
    currentUser.requireOwnerOrAdmin(inference.getUser_id());
    return Response.ok(inference).build();
  }

  /** Inferences of {@code user_id} (defaults to the caller); only the owner or an admin. */
  @GET
  @Path("/by-user")
  @RolesAllowed({ "admin", "user" })
  public Response getAllInferencesByUserId(@QueryParam("user_id") Long userId) {
    long target = userId != null ? userId : currentUser.id();
    currentUser.requireOwnerOrAdmin(target);
    List<InferenceDto> allInferences = inferenceRepository.findAllInferencesByUserId(target);
    return Response.ok(allInferences).build();
  }

  @GET
  @Path("/by-model")
  @RolesAllowed({ "admin", "user" })
  public Response getAllInferencesByModelId(@QueryParam("model_id") Long modelId) {
    long id = requireModelId(modelId);
    // Admins see every inference of the model; other users only their own.
    List<InferenceDto> allInferences = currentUser.isAdmin()
        ? inferenceRepository.findAllInferencesByModelId(id)
        : inferenceRepository.findAllInferencesByModelIdAndUserId(id, currentUser.id());
    return Response.ok(allInferences).build();
  }

  @GET
  @Path("/count")
  @RolesAllowed({ "admin", "user" })
  public Response getInferenceCount() {
    long count = inferenceRepository.count();
    return Response.ok(count).build();
  }

  /** Train + evaluate a model on the ML service. Writes a "ParamsFiles" resource for the caller. */
  @POST
  @Path("/model-train-eval-MPAP")
  @RolesAllowed({ "admin", "user" })
  public Response trainEval(
      @QueryParam("model_id") Long modelId,
      @QueryParam("pca_dim") @DefaultValue("0") int pcaDim) {
    return Response.ok(inferenceService.trainEvalMPAP(currentUser.id(), requireModelId(modelId), pcaDim)).build();
  }

  /** Run an inference on the uploaded {@code test_sample} (comma/whitespace-separated numbers). */
  @POST
  @Path("/model-inference-MPAP")
  @RolesAllowed({ "admin", "user" })
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response getInference(
      @QueryParam("model_id") Long modelId,
      @QueryParam("pca_dim") Integer pcaDim,
      @RestForm("test_sample") FileUpload input) {
    return Response.ok(
        inferenceService.inferenceMPAP(currentUser.id(), requireModelId(modelId), pcaDim, input)).build();
  }

  @DELETE
  @Path("/delete/{id}")
  @RolesAllowed({ "admin", "user" })
  public Response deleteInference(@PathParam("id") long id) {
    InferenceDto inference = inferenceRepository.findInferenceById(id)
        .orElseThrow(() -> HttpErrors.notFound("Inference not found"));
    currentUser.requireOwnerOrAdmin(inference.getUser_id());
    if (!inferenceRepository.deleteInference(id)) {
      throw HttpErrors.notFound("Inference not found");
    }
    return Response.ok("Inference " + id + " is deleted!").build();
  }

  private static long requireModelId(Long modelId) {
    if (modelId == null) {
      throw HttpErrors.badRequest("Query parameter 'model_id' is required");
    }
    return modelId;
  }
}
