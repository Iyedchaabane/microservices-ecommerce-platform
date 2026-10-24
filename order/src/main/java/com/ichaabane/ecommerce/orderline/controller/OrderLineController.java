package com.ichaabane.ecommerce.orderline.controller;


import com.ichaabane.ecommerce.orderline.dto.OrderLineResponse;
import com.ichaabane.ecommerce.orderline.service.OrderLineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/order-lines")
@RequiredArgsConstructor
@Slf4j
public class OrderLineController {

    private final OrderLineService service;

    @GetMapping("/order/{order-id}")
    public ResponseEntity<List<OrderLineResponse>> findByOrderId(
            @PathVariable("order-id") Integer orderId
    ) {
        log.debug("REST request to get order lines for order id={}", orderId);
        return ResponseEntity.ok(service.findAllByOrderId(orderId));
    }
}

