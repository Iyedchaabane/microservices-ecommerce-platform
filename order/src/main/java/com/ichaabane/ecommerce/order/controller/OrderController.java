package com.ichaabane.ecommerce.order.controller;

import java.util.List;

import com.ichaabane.ecommerce.order.dto.OrderRequest;
import com.ichaabane.ecommerce.order.dto.OrderResponse;
import com.ichaabane.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

  private final OrderService service;

  @PostMapping
  public ResponseEntity<Integer> createOrder(
      @RequestBody @Valid OrderRequest request
  ) {
    log.info("REST request to create order with reference='{}'", request.reference());
    var orderId = this.service.createOrder(request);
    log.info("REST response: order created with id={}", orderId);
    return ResponseEntity.ok(orderId);
  }

  @GetMapping
  public ResponseEntity<List<OrderResponse>> findAll() {
    log.debug("REST request to list all orders");
    return ResponseEntity.ok(this.service.findAllOrders());
  }

  @GetMapping("/{order-id}")
  public ResponseEntity<OrderResponse> findById(
      @PathVariable("order-id") Integer orderId
  ) {
    log.debug("REST request to get order id={}", orderId);
    return ResponseEntity.ok(this.service.findById(orderId));
  }
}

