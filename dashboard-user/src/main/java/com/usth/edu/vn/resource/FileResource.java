package com.usth.edu.vn.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Map;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.usth.edu.vn.model.Resources;
import com.usth.edu.vn.repository.ResourceRepository;
import com.usth.edu.vn.repository.UserRepository;
import com.usth.edu.vn.security.CurrentUser;
import com.usth.edu.vn.services.FileServices;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * File download / avatar endpoints. Generic uploads were removed: model and resource
 * files are uploaded through {@code /models/create} and {@code /resources/create}.
 * Legacy {@code user_id} query parameters are accepted but ignored.
 */
@Path("/files")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class FileResource {

  @Inject
  FileServices fileServices;

  @Inject
  CurrentUser currentUser;

  @Inject
  UserRepository userRepository;

  @Inject
  ResourceRepository resourceRepository;

  @Inject
  EntityManager entityManager;

  /**
   * Uploads (replaces) the caller's avatar. PNG or JPEG only. An admin may target another
   * user with {@code ?id=}; for anyone else {@code id} must be omitted or equal their own id.
   */
  @POST
  @Path("/upload-avatar")
  @RolesAllowed({ "admin", "user" })
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Map<String, String> uploadAvatar(
      @QueryParam("id") Long id,
      @RestForm("image") FileUpload input) throws IOException {
    long target = resolveTargetUser(id);
    fileServices.uploadAvatar(target, input);
    return Map.of("message", "Avatar updated");
  }

  @DELETE
  @Path("/delete-avatar")
  @RolesAllowed({ "admin", "user" })
  public Response deleteAvatar(@QueryParam("id") Long id) throws IOException {
    long target = resolveTargetUser(id);
    fileServices.deleteAvatar(target);
    return Response.noContent().build();
  }

  /** Public: returns the user's avatar image, 404 if none. */
  @GET
  @Path("/get-avatar")
  @PermitAll
  @Produces({ FileServices.IMAGE_PNG, FileServices.IMAGE_JPEG })
  public Response getAvatar(@QueryParam("id") Long id) throws IOException {
    if (id == null) {
      throw new NotFoundException("No avatar");
    }
    java.nio.file.Path avatar = fileServices.findAvatar(id)
        .orElseThrow(() -> new NotFoundException("No avatar uploaded"));
    String type = FileServices.detectImageType(avatar);
    if (type == null) {
      throw new NotFoundException("No avatar uploaded");
    }
    return Response.ok(avatar.toFile(), type).build();
  }

  /** Any authenticated user may download a model script. */
  @GET
  @Path("/get-model")
  @RolesAllowed({ "admin", "user" })
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getModelFile(@QueryParam("model_id") Long modelId) {
    if (modelId == null) {
      throw new NotFoundException("Model not found");
    }
    String filepath = entityManager
        .createQuery("select m.filepath from Models m where m.id = :id", String.class)
        .setParameter("id", modelId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Model not found"));
    return download(filepath, "Model file has not been uploaded");
  }

  /** Only the resource owner or an admin may download a resource file. */
  @GET
  @Path("/get-resource")
  @RolesAllowed({ "admin", "user" })
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getResourceFile(@QueryParam("resource_id") Long resourceId) {
    if (resourceId == null) {
      throw new NotFoundException("Resource not found");
    }
    Resources resource = resourceRepository.findByIdOptional(resourceId)
        .orElseThrow(() -> new NotFoundException("Resource not found"));
    currentUser.requireOwnerOrAdmin(resource.getUser() == null ? null : resource.getUser().getId());
    return download(resource.getFilepath(), "Resource file has not been uploaded");
  }

  private Response download(String relativePath, String missingMessage) {
    if (relativePath == null || relativePath.isBlank()) {
      throw new NotFoundException(missingMessage);
    }
    java.nio.file.Path file = fileServices.resolveStored(relativePath);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new NotFoundException(missingMessage);
    }
    return Response.ok(file.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        .header("Content-Disposition", contentDisposition(file.getFileName().toString()))
        .build();
  }

  private static String contentDisposition(String fileName) {
    String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    return "attachment; filename=\"" + safe + "\"";
  }

  /** Caller's own id, or {@code id} if the caller is an admin (404 if that user does not exist). */
  private long resolveTargetUser(Long id) {
    if (id == null) {
      return currentUser.id();
    }
    currentUser.requireOwnerOrAdmin(id);
    if (userRepository.findByIdOptional(id).isEmpty()) {
      throw new NotFoundException("User not found");
    }
    return id;
  }
}
