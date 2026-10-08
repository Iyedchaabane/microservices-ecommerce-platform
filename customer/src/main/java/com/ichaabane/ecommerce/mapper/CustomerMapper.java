package com.ichaabane.ecommerce.mapper;

import com.ichaabane.ecommerce.dto.request.CustomerCreateRequest;
import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.model.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

  /**
   * Maps a create request to a new entity. The identifier is intentionally never
   * set here: the create request carries no id and MongoDB generates one.
   */
  public Customer toCustomer(CustomerCreateRequest request) {
    if (request == null) {
      return null;
    }
    return Customer.builder()
        .firstname(request.firstname())
        .lastname(request.lastname())
        .email(request.email())
        .address(request.address())
        .build();
  }

  public CustomerResponse fromCustomer(Customer customer) {
    if (customer == null) {
      return null;
    }
    return new CustomerResponse(
        customer.getId(),
        customer.getFirstname(),
        customer.getLastname(),
        customer.getEmail(),
        customer.getAddress()
    );
  }
}
