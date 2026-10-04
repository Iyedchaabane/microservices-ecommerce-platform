package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.mapper.CustomerMapper;
import com.ichaabane.ecommerce.dto.request.CustomerRequest;
import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
import com.ichaabane.ecommerce.model.Customer;
import com.ichaabane.ecommerce.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private final CustomerRepository repository;
  private final CustomerMapper mapper;

  public String createCustomer(CustomerRequest request) {
    var customer = mapper.toCustomer(request);
    // A newly created customer must never inherit a client-supplied identifier,
    // otherwise an attacker could overwrite an existing document.
    customer.setId(null);
    return this.repository.save(customer).getId();
  }

  public void updateCustomer(CustomerRequest request) {
    // Without this guard a blank id would reach the repository and surface as an
    // unexpected 500. An update without an identifier is a client error.
    if (!StringUtils.hasText(request.id())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer ID is required to update a customer");
    }
    var customer = this.repository.findById(request.id())
        .orElseThrow(() -> new CustomerNotFoundException(
            String.format("Cannot update customer:: No customer found with the provided ID: %s", request.id())
        ));
    mergeCustomer(customer, request);
    this.repository.save(customer);
  }

  private void mergeCustomer(Customer customer, CustomerRequest request) {
    if (StringUtils.hasText(request.firstname())) {
      customer.setFirstname(request.firstname());
    }
    if (StringUtils.hasText(request.lastname())) {
      customer.setLastname(request.lastname());
    }
    if (StringUtils.hasText(request.email())) {
      customer.setEmail(request.email());
    }
    if (request.address() != null) {
      customer.setAddress(request.address());
    }
  }

  public List<CustomerResponse> findAllCustomers() {
    return  this.repository.findAll()
        .stream()
        .map(this.mapper::fromCustomer)
        .collect(Collectors.toList());
  }

  public CustomerResponse findById(String id) {
    return this.repository.findById(id)
        .map(mapper::fromCustomer)
        .orElseThrow(() -> new CustomerNotFoundException(String.format("No customer found with the provided ID: %s", id)));
  }

  public boolean existsById(String id) {
    return this.repository.existsById(id);
  }

  public void deleteCustomer(String id) {
    this.repository.deleteById(id);
  }
}
