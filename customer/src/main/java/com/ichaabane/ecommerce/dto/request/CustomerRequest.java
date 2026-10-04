package com.ichaabane.ecommerce.dto.request;

import com.ichaabane.ecommerce.model.Address;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
    String id,
    @NotBlank(message = "Customer firstname is required")
    String firstname,
    @NotBlank(message = "Customer lastname is required")
    String lastname,
    @NotBlank(message = "Customer Email is required")
    @Email(message = "Customer Email is not a valid email address")
    String email,
    Address address
) {

}
