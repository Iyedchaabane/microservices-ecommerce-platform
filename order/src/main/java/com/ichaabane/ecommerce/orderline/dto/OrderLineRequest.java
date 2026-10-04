package com.ichaabane.ecommerce.orderline.dto;

public record OrderLineRequest(
        Integer orderId,
        Integer productId,
        double quantity
) {
}
