package com.usth.edu.vn.resource;

import java.net.URI;
import java.util.List;

import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.model.Ratings;
import com.usth.edu.vn.model.Users;
import com.usth.edu.vn.model.dto.RatingDto;
import com.usth.edu.vn.model.dto.RatingRequest;
import com.usth.edu.vn.repository.ModelRepository;
import com.usth.edu.vn.repository.RatingRepository;
import com.usth.edu.vn.security.CurrentUser;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Path("/ratings")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RatingResource {

  @Inject
  RatingRepository ratingRepository;

  @Inject
  ModelRepository modelRepository;

  @Inject
  CurrentUser currentUser;

  @GET
  @Path("/")
  @PermitAll
  public Response getAllRatings() {
    List<RatingDto> allRatings = ratingRepository.findAllRatings();
    return Response.ok(allRatings).build();
  }

  @GET
  @Path("/{id}")
  @PermitAll
  public Response getRatingById(@PathParam("id") long id) {
    return Response.ok(ratingRepository.findRatingById(id)).build();
  }

  @GET
  @Path("/count")
  @PermitAll
  public Response getRatingCount() {
    return Response.ok(ratingRepository.count()).build();
  }

  @GET
  @Path("/model_id")
  @PermitAll
  public Response getRatingsByModelId(@QueryParam("model_id") @NotNull Long modelId) {
    return Response.ok(ratingRepository.findRatingByModelId(modelId)).build();
  }

  @GET
  @Path("/user_id")
  @PermitAll
  public Response getRatingsByUserId(@QueryParam("user_id") @NotNull Long userId) {
    return Response.ok(ratingRepository.findRatingByUserId(userId)).build();
  }

  /** The caller rates a model (once per model). 201 with the rating, 409 if already rated. */
  @POST
  @Path("/create")
  @RolesAllowed({ "admin", "user" })
  @Transactional
  public Response createRating(
      @QueryParam("model_id") @NotNull Long modelId,
      @Valid @NotNull RatingRequest request) {
    if (request.stars() == null) {
      throw new BadRequestException("stars is required (1-5)");
    }
    Models model = modelRepository.findByIdOptional(modelId)
        .orElseThrow(() -> new NotFoundException("Model " + modelId + " not found"));
    Users rater = currentUser.user();
    Ratings rating = ratingRepository.addRating(rater, model, request.stars(), request.comment());
    return Response.created(URI.create("/ratings/" + rating.getId()))
        .entity(ratingRepository.findRatingById(rating.getId()))
        .build();
  }

  /** The caller updates their own rating of a model. 200 with the updated rating. */
  @PUT
  @Path("/update")
  @RolesAllowed({ "admin", "user" })
  @Transactional
  public Response updateRating(
      @QueryParam("model_id") @NotNull Long modelId,
      @Valid @NotNull RatingRequest request) {
    Ratings rating = ratingRepository.findByUserAndModel(currentUser.id(), modelId)
        .orElseThrow(() -> new NotFoundException("You have not rated model " + modelId));
    if (request.stars() != null) {
      rating.setStars(request.stars());
    }
    if (request.comment() != null) {
      rating.setComment(request.comment());
    }
    return Response.ok(ratingRepository.findRatingById(rating.getId())).build();
  }

  /** Deletes a rating; its author or an admin only. */
  @DELETE
  @Path("/delete/{id}")
  @RolesAllowed({ "admin", "user" })
  @Transactional
  @Produces(MediaType.TEXT_PLAIN)
  public Response deleteRating(@PathParam("id") long id) {
    Ratings rating = ratingRepository.findByIdOptional(id)
        .orElseThrow(() -> new NotFoundException("Rating " + id + " not found"));
    currentUser.requireOwnerOrAdmin(rating.getUser() == null ? null : rating.getUser().getId());
    ratingRepository.delete(rating);
    return Response.ok("Rating " + id + " is deleted!").build();
  }
}
