package com.ichaabane.ecommerce.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;

@Validated
public record Customer(
        String id,
        @NotBlank(message = "Firstname is required")
        String firstname,
        @NotBlank(message = "Lastname is required")
        String lastname,
        @NotBlank(message = "Email is required")
        @Email(message = "The customer email is not correctly formatted")
        String email
) { }
