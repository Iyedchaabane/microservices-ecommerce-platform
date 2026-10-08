package com.ichaabane.ecommerce.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomerEmailAlreadyExistsException extends RuntimeException {

  private final String msg;
}
