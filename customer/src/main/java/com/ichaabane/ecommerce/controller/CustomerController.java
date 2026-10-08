package com.ichaabane.ecommerce.controller;

import java.net.URI;

import com.ichaabane.ecommerce.dto.request.CustomerCreateRequest;
import com.ichaabane.ecommerce.dto.request.CustomerUpdateRequest;
import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

  private static final int DEFAULT_PAGE_SIZE = 10;

  private final CustomerService service;

  @PostMapping
  public ResponseEntity<String> createCustomer(
      @RequestBody @Valid CustomerCreateRequest request
  ) {
    var customerId = this.service.createCustomer(request);
    return ResponseEntity
        .created(URI.create("/api/v1/customers/" + customerId))
        .body(customerId);
  }

  @PutMapping("/{customer-id}")
  public ResponseEntity<Void> updateCustomer(
      @PathVariable("customer-id") String customerId,
      @RequestBody @Valid CustomerUpdateRequest request
  ) {
    this.service.updateCustomer(customerId, request);
    return ResponseEntity.accepted().build();
  }

  @GetMapping
  public ResponseEntity<Page<CustomerResponse>> findAll(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
    return ResponseEntity.ok(this.service.findAllCustomers(pageable));
  }

  @GetMapping("/exists/{customer-id}")
  public ResponseEntity<Boolean> existsById(
      @PathVariable("customer-id") String customerId
  ) {
    return ResponseEntity.ok(this.service.existsById(customerId));
  }

  @GetMapping("/{customer-id}")
  public ResponseEntity<CustomerResponse> findById(
      @PathVariable("customer-id") String customerId
  ) {
    return ResponseEntity.ok(this.service.findById(customerId));
  }

  @DeleteMapping("/{customer-id}")
  public ResponseEntity<Void> delete(
      @PathVariable("customer-id") String customerId
  ) {
    this.service.deleteCustomer(customerId);
    return ResponseEntity.noContent().build();
  }

  private static int normalizeSize(int size) {
    if (size < 1) {
      return 1;
    }
    return Math.min(size, 100);
  }
}
