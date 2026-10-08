package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.dto.request.CustomerCreateRequest;
import com.ichaabane.ecommerce.dto.request.CustomerUpdateRequest;
import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.exception.CustomerEmailAlreadyExistsException;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
import com.ichaabane.ecommerce.mapper.CustomerMapper;
import com.ichaabane.ecommerce.model.Customer;
import com.ichaabane.ecommerce.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private final CustomerRepository repository;
  private final CustomerMapper mapper;

  public String createCustomer(CustomerCreateRequest request) {
    // Emails are normalized (trim + lowercase) so John@Email.com and john@email.com
    // are the same identity, both in the pre-check and in the stored document.
    var customer = mapper.toCustomer(request);
    customer.setEmail(normalizeEmail(request.email()));
    // A newly created customer must never inherit a client-supplied identifier,
    // otherwise an attacker could overwrite an existing document.
    customer.setId(null);
    assertEmailIsAvailable(customer.getEmail(), null);
    try {
      return this.repository.save(customer).getId();
    } catch (DuplicateKeyException e) {
      // The MongoDB unique index on email is the final guarantee: a concurrent
      // request may have inserted the same email after our pre-check.
      throw new CustomerEmailAlreadyExistsException(
          "Customer with email " + customer.getEmail() + " already exists");
    }
  }

  public void updateCustomer(String customerId, CustomerUpdateRequest request) {
    var customer = this.repository.findById(customerId)
        .orElseThrow(() -> new CustomerNotFoundException(
            String.format("Cannot update customer:: No customer found with the provided ID: %s", customerId)
        ));
    // Validate before merging so a rejected update leaves the loaded entity untouched.
    if (StringUtils.hasText(request.email())) {
      var normalizedEmail = normalizeEmail(request.email());
      assertEmailIsAvailable(normalizedEmail, customer.getId());
      customer.setEmail(normalizedEmail);
    }
    mergeCustomer(customer, request);
    try {
      this.repository.save(customer);
    } catch (DuplicateKeyException e) {
      // Same race as on create: the unique index decides, we translate the failure.
      throw new CustomerEmailAlreadyExistsException(
          "Customer with email " + customer.getEmail() + " already exists");
    }
  }

  /**
   * Ensures no other customer already uses the given email.
   *
   * <p>This is a fast-fail convenience check only — the MongoDB unique index on
   * {@code email} remains the authoritative guarantee against concurrent writes.</p>
   *
   * @param email     the (already normalized) email to check
   * @param currentId id of the customer being updated, whose own email must be
   *                  ignored; pass {@code null} on creation
   */
  private void assertEmailIsAvailable(String email, String currentId) {
    if (email == null) {
      return;
    }
    this.repository.findByEmailIgnoreCase(email)
        .filter(existing -> !existing.getId().equals(currentId))
        .ifPresent(existing -> {
          throw new CustomerEmailAlreadyExistsException(
              "Customer with email " + email + " already exists");
        });
  }

  private void mergeCustomer(Customer customer, CustomerUpdateRequest request) {
    if (StringUtils.hasText(request.firstname())) {
      customer.setFirstname(request.firstname());
    }
    if (StringUtils.hasText(request.lastname())) {
      customer.setLastname(request.lastname());
    }
    // The email was already normalized and set above (after the availability check).
    if (request.address() != null) {
      customer.setAddress(request.address());
    }
  }

  /**
   * Normalizes an email address so that it is stored and compared consistently:
   * surrounding whitespace removed, lowercased with {@link Locale#ROOT}.
   */
  private String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  public Page<CustomerResponse> findAllCustomers(Pageable pageable) {
    return this.repository.findAll(pageable).map(this.mapper::fromCustomer);
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
    // deleteById is a silent no-op for a missing document; check existence first
    // so clients get a predictable 404 instead of a fake success.
    if (!this.repository.existsById(id)) {
      throw new CustomerNotFoundException(String.format("No customer found with the provided ID: %s", id));
    }
    this.repository.deleteById(id);
  }
}
