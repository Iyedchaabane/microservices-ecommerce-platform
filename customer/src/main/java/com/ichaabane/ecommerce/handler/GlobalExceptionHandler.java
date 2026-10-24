package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.exception.CustomerEmailAlreadyExistsException;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomerNotFoundException.class)
  public ResponseEntity<String> handle(CustomerNotFoundException exp) {
    log.warn("Customer not found: {}", exp.getMsg());
    return ResponseEntity
        .status(NOT_FOUND)
        .body(exp.getMsg());
  }

  @ExceptionHandler(CustomerEmailAlreadyExistsException.class)
  public ResponseEntity<String> handle(CustomerEmailAlreadyExistsException exp) {
    // Same conflict as the client body, but without echoing the raw customer
    // email: the client already receives it, the log doesn't need PII.
    log.warn("Customer email conflict: an account with the submitted email already exists");
    return ResponseEntity
        .status(CONFLICT)
        .body(exp.getMsg());
  }

  /**
   * Safety net for duplicate-key failures that reach the web layer untranslated
   * (e.g. from a code path without its own handling). The MongoDB error details
   * are never exposed to the client.
   */
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<String> handleDuplicateKey(DuplicateKeyException exp) {
    // Safe, generic message only: MongoDB duplicate-key errors embed the raw
    // duplicated value (the customer email), which must never reach the logs.
    // The client-facing body discharges no internals either.
    log.warn("Duplicate key constraint violation in customer service: uniqueness constraint violated for submitted data");
    return ResponseEntity
        .status(CONFLICT)
        .body("The request conflicts with an existing resource");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exp) {
    var errors = new HashMap<String, String>();
    exp.getBindingResult().getAllErrors()
            .forEach(error -> {
              // Global (non-field) errors have no field name; only cast when safe.
              if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
              }
            });

    log.warn("Customer validation failure: {}", errors);
    return ResponseEntity
            .status(BAD_REQUEST)
            .body(new ErrorResponse(errors));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleGenericException(Exception exp) {
    log.error("Unexpected error in customer service: {}", exp.getMessage(), exp);
    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("An unexpected error occurred. Please try again later.");
  }
}
