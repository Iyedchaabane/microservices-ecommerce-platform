package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<String> handle(EntityNotFoundException exp) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(exp.getMessage());
  }

  /**
   * Concurrent duplicate inserts (raced same-order payments) and other DB
   * constraint violations. The raw message embeds driver/SQL details that
   * must not reach the client, so the response body is a fixed generic text
   * and the full exception goes to the logs.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException exp) {
    log.warn("Data integrity violation while processing a payment request", exp);
    return ResponseEntity
        .status(CONFLICT)
        .body("The request conflicts with existing data (e.g. a payment already exists for this order)");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exp) {
    var errors = new HashMap<String, String>();
    exp.getBindingResult().getAllErrors()
            .forEach(error -> {
              // getAllErrors() also yields ObjectError instances for class-level
              // constraints, which have no field — key those by object name instead
              // of casting blindly (a blind cast would turn a validation failure
              // into a 500 via ClassCastException). Same fix as in product-service.
              if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), error.getDefaultMessage());
              } else {
                errors.put(error.getObjectName(), error.getDefaultMessage());
              }
            });

    return ResponseEntity
            .status(BAD_REQUEST)
            .body(new ErrorResponse(errors));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<String> handle(BusinessException exp) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(exp.getMsg());
  }
}
