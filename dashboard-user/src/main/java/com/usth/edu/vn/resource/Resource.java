package com.usth.edu.vn.resource;

import static com.usth.edu.vn.services.FileName.RESOURCES;

import java.io.IOException;
import java.util.List;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.usth.edu.vn.model.Resources;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.ResourceDto;
import com.usth.edu.vn.model.dto.ResourceUpdateRequest;
import com.usth.edu.vn.repository.ResourceRepository;
import com.usth.edu.vn.security.CurrentUser;
import com.usth.edu.vn.services.FileServices;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

import static jakarta.ws.rs.core.Response.Status.CREATED;

@Path("/resources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({ "admin", "user" })
public class Resource {

  @Inject
  ResourceRepository resourceRepository;

  @Inject
  FileServices fileServices;

  @Inject
  CurrentUser currentUser;

  /** All resources of all users (admin only). */
  @GET
  @RolesAllowed("admin")
  public List<ResourceDto> getAllResources() {
    return resourceRepository.findAllResources();
  }

  @GET
  @Path("/{id}")
  public ResourceDto getResourceById(@PathParam("id") long id) {
    ResourceDto dto = resourceRepository.findResourceById(id)
        .orElseThrow(() -> new NotFoundException("Resource not found"));
    currentUser.requireOwnerOrAdmin(dto.getUser_id());
    return dto;
  }

  /** Resources of {@code user_id} (defaults to the caller); only the owner or an admin. */
  @GET
  @Path("/user_id")
  public List<ResourceDto> getResourceByUserId(@QueryParam("user_id") Long userId) {
    long target = userId == null ? currentUser.id() : userId;
    currentUser.requireOwnerOrAdmin(target);
    return resourceRepository.findResourceByUserId(target);
  }

  @GET
  @Path("/count")
  public long getResourceCount() {
    return resourceRepository.count();
  }

  /**
   * Uploads a resource file for the caller. The owner is always the authenticated
   * user; a legacy {@code user_id} query parameter is ignored.
   */
  @POST
  @Path("/create")
  @Transactional
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response createResource(
      @QueryParam("type") String type,
      @RestForm("resource") FileUpload input) throws IOException {
    if (!FileServices.isValidType(type)) {
      throw new BadRequestException("Query parameter 'type' is required and must match [A-Za-z0-9_-]{1,64}");
    }
    if (input == null) {
      throw new BadRequestException("Missing multipart file field 'resource'");
    }
    Users owner = currentUser.user();
    String relativePath = fileServices.uploadFile(owner.getId(), input, RESOURCES, type);
    // remove the stored file again if the insert below fails / the transaction rolls back
    fileServices.deleteStoredFileOnRollback(relativePath);
    Resources resource = resourceRepository.createResource(owner, type, relativePath);
    return Response.status(CREATED).entity(ResourceRepository.toDto(resource)).build();
  }

  /** Changes the resource type only (the file path can never be set by clients). */
  @PUT
  @Path("/update")
  @Transactional
  public ResourceDto updateResource(@QueryParam("id") @NotNull Long id, @Valid @NotNull ResourceUpdateRequest request) {
    Resources resource = resourceRepository.findByIdOptional(id)
        .orElseThrow(() -> new NotFoundException("Resource not found"));
    currentUser.requireOwnerOrAdmin(resource.getUser() == null ? null : resource.getUser().getId());
    resource.setType(request.type());
    return ResourceRepository.toDto(resource);
  }

  @DELETE
  @Path("/delete/{id}")
  @Transactional
  @Produces(MediaType.TEXT_PLAIN)
  public String deleteResource(@PathParam("id") long id) {
    Resources resource = resourceRepository.findByIdOptional(id)
        .orElseThrow(() -> new NotFoundException("Resource not found"));
    currentUser.requireOwnerOrAdmin(resource.getUser() == null ? null : resource.getUser().getId());
    String filepath = resource.getFilepath();
    resourceRepository.delete(resource);
    if (filepath != null) {
      try {
        fileServices.deleteStoredFileAfterCommit(filepath);
      } catch (ForbiddenException e) {
        // legacy/foreign path outside the storage root: drop the row, never touch the file
      }
    }
    return "Resource " + id + " is deleted!";
  }
}
