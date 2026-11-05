package com.usth.edu.vn.services.ml;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Builds JAX-RS exceptions that carry a plain-text message body. */
public final class HttpErrors {

  private HttpErrors() {
  }

  public static BadRequestException badRequest(String message) {
    return new BadRequestException(message, response(400, message));
  }

  public static NotFoundException notFound(String message) {
    return new NotFoundException(message, response(404, message));
  }

  public static WebApplicationException status(int status, String message) {
    return new WebApplicationException(message, response(status, message));
  }

  private static Response response(int status, String message) {
    return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN).build();
  }
}
