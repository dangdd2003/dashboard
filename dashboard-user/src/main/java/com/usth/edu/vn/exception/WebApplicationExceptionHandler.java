package com.usth.edu.vn.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Renders {@link WebApplicationException}s (NotFoundException, ForbiddenException, BadRequestException,
 * ClientErrorException(msg, 409), ...) as a {@code text/plain} body containing only the exception message,
 * with the exception's status and headers (e.g. {@code WWW-Authenticate}). Never includes stack traces.
 * A response that already carries an entity, or a non-error (e.g. redirect) response, is returned unchanged.
 */
@Provider
public class WebApplicationExceptionHandler implements ExceptionMapper<WebApplicationException> {

  @Override
  public Response toResponse(WebApplicationException e) {
    Response original = e.getResponse();
    if (original == null) {
      return Response.serverError()
          .entity("Internal server error")
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
    if (original.getStatus() < 400 || original.hasEntity()) {
      return original;
    }
    String message = e.getMessage();
    if (message == null || message.isBlank()) {
      Response.StatusType statusInfo = original.getStatusInfo();
      message = statusInfo != null && statusInfo.getReasonPhrase() != null
          ? statusInfo.getReasonPhrase()
          : "HTTP " + original.getStatus();
    }
    return Response.fromResponse(original)
        .entity(message)
        .type(MediaType.TEXT_PLAIN)
        .build();
  }
}
