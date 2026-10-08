package com.ichaabane.ecommerce.dto.request;

import com.ichaabane.ecommerce.model.Address;
import jakarta.validation.constraints.Email;

/**
 * Payload for {@code PUT /api/v1/customers/{customer-id}}.
 *
 * <p>The service merges only the provided (non-null / non-blank) fields, so the
 * fields are intentionally optional — no {@code @NotBlank} here. The customer id
 * comes from the path, never from the body.</p>
 */
public record CustomerUpdateRequest(
    String firstname,
    String lastname,
    @Email(message = "Customer Email is not a valid email address")
    String email,
    Address address
) {

}
