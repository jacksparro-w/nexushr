package com.nexushr.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiError> status(ResponseStatusException error, HttpServletRequest request) {
    HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
    return response(status, error.getReason() == null ? status.getReasonPhrase() : error.getReason(), request, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError field : error.getBindingResult().getFieldErrors()) fields.putIfAbsent(field.getField(), field.getDefaultMessage());
    return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, fields);
  }

  @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ApiError> badRequest(Exception error, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "Request is invalid or malformed", request, Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> forbidden(AccessDeniedException error, HttpServletRequest request) {
    return response(HttpStatus.FORBIDDEN, "Access denied", request, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception error, HttpServletRequest request) {
    log.error("Unhandled API error for {} {}", request.getMethod(), request.getRequestURI(), error);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred", request, Map.of());
  }

  private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request,
      Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
        status.getReasonPhrase(), message, request.getRequestURI(), fields));
  }

  public record ApiError(Instant timestamp, int status, String error, String message, String path,
      Map<String, String> fieldErrors) {}
}
