package com.usth.edu.vn.services.ml;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Typed client for the Python ML service.
 *
 * Base URL and timeouts come from {@code quarkus.rest-client.python-service.*}.
 * Path parameters are URL-encoded by the client; callers must still validate
 * them (see {@code InferenceService}). A {@code null} query parameter is omitted.
 */
@RegisterRestClient(configKey = "python-service")
@Path("/model")
public interface PythonModelClient {

  /** Train + evaluate a model: {@code GET /model/{owner}/{type}/{name}/run?ndim=&user=}. */
  @GET
  @Path("/{owner}/{type}/{name}/run")
  @Produces(MediaType.APPLICATION_JSON)
  TrainingResponse run(
      @PathParam("owner") long owner,
      @PathParam("type") String type,
      @PathParam("name") String name,
      @QueryParam("ndim") Integer ndim,
      @QueryParam("user") long user);

  /**
   * Run an inference: {@code POST /model/{owner}/{type}/{name}/inference?ndim=&user=}
   * with body {@code {"input": [...]}}. Returned as a tree so the raw JSON can be stored.
   */
  @POST
  @Path("/{owner}/{type}/{name}/inference")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  JsonNode inference(
      @PathParam("owner") long owner,
      @PathParam("type") String type,
      @PathParam("name") String name,
      @QueryParam("ndim") Integer ndim,
      @QueryParam("user") long user,
      InferenceRequest request);
}
