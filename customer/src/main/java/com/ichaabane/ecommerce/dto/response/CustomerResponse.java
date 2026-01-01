package com.ichaabane.ecommerce.dto.response;

import com.ichaabane.ecommerce.model.Address;

public record CustomerResponse(
    String id,
    String firstname,
    String lastname,
    String email,
    Address address
) {

}
