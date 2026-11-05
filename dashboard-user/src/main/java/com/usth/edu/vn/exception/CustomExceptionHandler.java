package com.usth.edu.vn.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Maps {@link CustomException}: USER_NOT_FOUND -> 404, USER_EXISTED -> 409, everything else -> 400.
 */
@Provider
public class CustomExceptionHandler implements ExceptionMapper<CustomException> {

  @Override
  public Response toResponse(CustomException e) {
    String message = e.getMessage() == null ? "" : e.getMessage();
    return switch (message) {
      case ExceptionType.USER_NOT_FOUND -> text(NOT_FOUND, "User not found!");
      case ExceptionType.USER_EXISTED -> text(CONFLICT, "User is already existed!");
      case ExceptionType.INCORRECT_PASSWORD -> text(BAD_REQUEST, "Password is not matched!");
      default -> text(BAD_REQUEST, message);
    };
  }

  private static Response text(Response.Status status, String body) {
    return Response.status(status)
        .entity(body)
        .type(MediaType.TEXT_PLAIN)
        .build();
  }
}
