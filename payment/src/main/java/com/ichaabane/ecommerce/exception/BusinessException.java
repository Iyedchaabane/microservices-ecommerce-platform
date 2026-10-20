package com.ichaabane.ecommerce.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// @Data on an exception also generated equals/hashCode/toString variants tied
// to the message only, plus the required-args constructor; @Getter + explicit
// equals/constructor keep exactly what callers rely on (getMsg) without the
// misleading generated API.
@RequiredArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
public class BusinessException extends RuntimeException {

  private final String msg;
}
