package com.usth.edu.vn.exception;

import jakarta.persistence.NoResultException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

/** {@code getSingleResult()} with no row means the requested entity does not exist -> 404. */
@Provider
public class NoResultExceptionHandler implements ExceptionMapper<NoResultException> {

  @Override
  public Response toResponse(NoResultException e) {
    return Response.status(NOT_FOUND)
        .entity("Not found!")
        .type(MediaType.TEXT_PLAIN)
        .build();
  }
}
