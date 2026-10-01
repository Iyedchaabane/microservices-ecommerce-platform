package com.ichaabane.ecommerce.payment;

import com.ichaabane.ecommerce.customer.CustomerResponse;
import com.ichaabane.ecommerce.order.model.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
    BigDecimal amount,
    PaymentMethod paymentMethod,
    Integer orderId,
    String orderReference,
    CustomerResponse customer
) {
}
