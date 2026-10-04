package com.ichaabane.ecommerce.order.dto;

import com.ichaabane.ecommerce.order.model.PaymentMethod;
import com.ichaabane.ecommerce.product.PurchaseRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(Include.NON_EMPTY)
public record OrderRequest(
    @NotBlank(message = "Order reference is required")
    String reference,
    @NotNull(message = "Payment method should be precised")
    PaymentMethod paymentMethod,
    @NotNull(message = "Customer should be present")
    @NotEmpty(message = "Customer should be present")
    @NotBlank(message = "Customer should be present")
    String customerId,
    @NotEmpty(message = "You should at least purchase one product")
    List<@Valid PurchaseRequest> products
) {

}
