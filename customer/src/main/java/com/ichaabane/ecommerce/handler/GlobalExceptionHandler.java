package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.exception.CustomerEmailAlreadyExistsException;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
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
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomerNotFoundException.class)
  public ResponseEntity<String> handle(CustomerNotFoundException exp) {
    return ResponseEntity
        .status(NOT_FOUND)
        .body(exp.getMsg());
  }

  @ExceptionHandler(CustomerEmailAlreadyExistsException.class)
  public ResponseEntity<String> handle(CustomerEmailAlreadyExistsException exp) {
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

    return ResponseEntity
            .status(BAD_REQUEST)
            .body(new ErrorResponse(errors));
  }
}
