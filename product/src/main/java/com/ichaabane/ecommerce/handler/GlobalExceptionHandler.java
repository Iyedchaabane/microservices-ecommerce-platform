package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import com.ichaabane.ecommerce.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductPurchaseException.class)
    public ResponseEntity<String> handle(ProductPurchaseException exp) {
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(exp.getMessage());
    }

    /**
     * A resource lookup that misses is 404, not 400: the request syntax is
     * valid, the referenced resource simply does not exist.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handle(ResourceNotFoundException exp) {
        return ResponseEntity
                .status(NOT_FOUND)
                .body(exp.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handle(EntityNotFoundException exp) {
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(exp.getMessage());
    }

    /**
     * Non-numeric ids and unsupported sort directions (from
     * {@code MethodArgumentTypeMismatchException}) are client errors; expose
     * only the offending value, never the stack trace.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exp) {
        var errors = new HashMap<String, String>();
        errors.put(exp.getName(), "Invalid value: " + exp.getValue());
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * Constraint violations coming from method validation on controller
     * parameters (e.g. page size bounds, order-by whitelist) render the same
     * keyed format as body validation, so the client sees one error shape.
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(jakarta.validation.ConstraintViolationException exp) {
        var errors = new HashMap<String, String>();
        exp.getConstraintViolations()
                .forEach(violation -> {
                    String path = violation.getPropertyPath().toString();
                    String field = path.substring(path.lastIndexOf('.') + 1);
                    errors.put(field, violation.getMessage());
                });
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * IllegalArgument raised inside the service (bad sort field, bad page)
     * must surface as 400, not as a 500 with a stack trace.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exp) {
        var errors = new HashMap<String, String>(Map.of("request", exp.getMessage()));
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exp) {
        var errors = new HashMap<String, String>();
        exp.getBindingResult().getAllErrors()
                .forEach(error -> {
                    // getAllErrors() also yields ObjectError instances for class-level
                    // constraints, which have no field — key those by object name instead
                    // of casting blindly (a blind cast would turn a validation failure
                    // into a 500 via ClassCastException).
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
}
