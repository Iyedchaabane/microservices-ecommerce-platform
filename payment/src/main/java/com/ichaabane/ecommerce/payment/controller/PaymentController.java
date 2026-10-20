package com.ichaabane.ecommerce.payment.controller;

import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService service;

  @PostMapping
  public ResponseEntity<Integer> createPayment(
      @RequestBody @Valid PaymentRequest request
  ) {
    Integer id = service.createPayment(request);
    // 201 + Location for a successful creation, aligning the status code with
    // the other services; Feign callers only read the 2xx status and body, so
    // this stays consume-compatible for order-service.
    return ResponseEntity.created(URI.create("/api/v1/payments/" + id)).body(id);
  }
}
