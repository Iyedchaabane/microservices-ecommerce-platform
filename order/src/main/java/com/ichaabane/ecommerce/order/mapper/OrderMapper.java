package com.ichaabane.ecommerce.order.mapper;

import com.ichaabane.ecommerce.order.dto.OrderRequest;
import com.ichaabane.ecommerce.order.dto.OrderResponse;
import com.ichaabane.ecommerce.order.model.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderMapper {

  public Order toOrder(OrderRequest request, BigDecimal totalAmount) {
    if (request == null) {
      return null;
    }
    return Order.builder()
        .reference(request.reference())
        .totalAmount(totalAmount)
        .paymentMethod(request.paymentMethod())
        .customerId(request.customerId())
        .build();
  }

  public OrderResponse fromOrder(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getReference(),
        order.getTotalAmount(),
        order.getPaymentMethod(),
        order.getCustomerId()
    );
  }
}
