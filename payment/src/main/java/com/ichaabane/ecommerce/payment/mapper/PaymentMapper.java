package com.ichaabane.ecommerce.payment.mapper;

import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.model.Payment;
import org.springframework.stereotype.Service;

@Service
public class PaymentMapper {

  public Payment toPayment(PaymentRequest request) {
    if (request == null) {
      return null;
    }
    return Payment.builder()
        .paymentMethod(request.paymentMethod())
        .amount(request.amount())
        .orderId(request.orderId())
        .orderReference(request.orderReference())
        .build();
  }
}
