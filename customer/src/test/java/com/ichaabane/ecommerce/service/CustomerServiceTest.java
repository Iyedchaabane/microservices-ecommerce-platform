package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.dto.request.CustomerCreateRequest;
import com.ichaabane.ecommerce.dto.request.CustomerUpdateRequest;
import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.exception.CustomerEmailAlreadyExistsException;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
import com.ichaabane.ecommerce.mapper.CustomerMapper;
import com.ichaabane.ecommerce.model.Address;
import com.ichaabane.ecommerce.model.Customer;
import com.ichaabane.ecommerce.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Unit Tests")
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    // Real mapper (spied): the service logic depends on what the mapper really produces.
    @Spy
    private CustomerMapper mapper = new CustomerMapper();

    private CustomerService service;

    private Address address;
    private Customer customer;
    private Customer customer2;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository, mapper);
        address = new Address("Main St", "12", "75001");
        customer = Customer.builder()
                .id("123").firstname("John").lastname("Doe").email("john@test.com").address(address).build();
        customer2 = Customer.builder()
                .id("456").firstname("Jane").lastname("Smith").email("jane@test.com").build();
    }

    @Nested
    @DisplayName("createCustomer() method")
    class CreateCustomer {

        @Test
        @DisplayName("Should save the customer and return the generated id")
        void shouldCreateCustomer() {
            var request = new CustomerCreateRequest("John", "Doe", "john@test.com", address);
            when(repository.findByEmailIgnoreCase("john@test.com")).thenReturn(Optional.empty());
            when(repository.save(any(Customer.class))).thenAnswer(inv -> {
                Customer saved = inv.getArgument(0);
                saved.setId("generated-id");
                return saved;
            });

            String id = service.createCustomer(request);

            assertEquals("generated-id", id);
            var captor = ArgumentCaptor.forClass(Customer.class);
            verify(repository, times(1)).save(captor.capture());
            assertEquals("John", captor.getValue().getFirstname());
            assertEquals("Doe", captor.getValue().getLastname());
            assertEquals("john@test.com", captor.getValue().getEmail());
            assertSame(address, captor.getValue().getAddress());
        }

        @Test
        @DisplayName("Should never set an id on a created customer (no client-controlled identifier)")
        void shouldNeverSetAnIdOnCreate() {
            // The create DTO carries no id at all, so the built entity cannot have one.
            var request = new CustomerCreateRequest("John", "Doe", "john@test.com", null);
            when(repository.findByEmailIgnoreCase("john@test.com")).thenReturn(Optional.empty());
            when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createCustomer(request);

            var captor = ArgumentCaptor.forClass(Customer.class);
            verify(repository).save(captor.capture());
            assertNull(captor.getValue().getId());
        }

        @Test
        @DisplayName("Should normalize the email (trim + lowercase) before storing it")
        void shouldNormalizeEmailOnCreate() {
            var request = new CustomerCreateRequest("John", "Doe", "  John@Test.COM  ", null);
            when(repository.findByEmailIgnoreCase("john@test.com")).thenReturn(Optional.empty());
            when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createCustomer(request);

            var captor = ArgumentCaptor.forClass(Customer.class);
            verify(repository).save(captor.capture());
            assertEquals("john@test.com", captor.getValue().getEmail());
        }

        @Test
        @DisplayName("Should reject the creation when the email already exists")
        void shouldRejectDuplicateEmailOnCreate() {
            var request = new CustomerCreateRequest("John", "Doe", "john@test.com", null);
            when(repository.findByEmailIgnoreCase("john@test.com"))
                    .thenReturn(Optional.of(customer));

            var exception = assertThrows(CustomerEmailAlreadyExistsException.class,
                    () -> service.createCustomer(request));

            assertTrue(exception.getMsg().contains("john@test.com"));
            verify(repository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should reject the creation when the email differs only by case or whitespace")
        void shouldRejectDuplicateEmailWithDifferentCase() {
            var request = new CustomerCreateRequest("John", "Doe", "  JOHN@test.com ", null);
            when(repository.findByEmailIgnoreCase("john@test.com"))
                    .thenReturn(Optional.of(customer));

            var exception = assertThrows(CustomerEmailAlreadyExistsException.class,
                    () -> service.createCustomer(request));

            assertTrue(exception.getMsg().contains("john@test.com"));
            verify(repository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should translate a database DuplicateKeyException into a domain 409 exception")
        void shouldHandleDuplicateKeyRaceOnCreate() {
            var request = new CustomerCreateRequest("John", "Doe", "john@test.com", null);
            when(repository.findByEmailIgnoreCase("john@test.com")).thenReturn(Optional.empty());
            // Simulates a concurrent request winning the unique-index race.
            when(repository.save(any(Customer.class)))
                    .thenThrow(new DuplicateKeyException("E11000 duplicate key error collection: customer.customer index: email"));

            var exception = assertThrows(CustomerEmailAlreadyExistsException.class,
                    () -> service.createCustomer(request));

            assertTrue(exception.getMsg().contains("john@test.com"));
            // The MongoDB internal error must not leak through the domain exception.
            assertFalse(exception.getMsg().contains("E11000"));
        }

        @Test
        @DisplayName("Should propagate a repository failure")
        void shouldPropagateRepositoryFailure() {
            var request = new CustomerCreateRequest("John", "Doe", "john@test.com", null);
            when(repository.findByEmailIgnoreCase("john@test.com")).thenReturn(Optional.empty());
            when(repository.save(any(Customer.class))).thenThrow(new RuntimeException("Database connection failed"));

            var exception = assertThrows(RuntimeException.class, () -> service.createCustomer(request));

            assertEquals("Database connection failed", exception.getMessage());
            verify(mapper, times(1)).toCustomer(request);
            verify(repository, times(1)).save(any(Customer.class));
        }
    }

    @Nested
    @DisplayName("updateCustomer() method")
    class UpdateCustomer {

        @Test
        @DisplayName("Should update every provided field, including the lastname")
        void shouldUpdateAllFields() {
            var newAddress = new Address("Other St", "99", "13001");
            when(repository.findById("123")).thenReturn(Optional.of(customer));

            service.updateCustomer("123", new CustomerUpdateRequest("Salma", "Newname", "salma@test.com", newAddress));

            assertEquals("Salma", customer.getFirstname());
            assertEquals("Newname", customer.getLastname());
            assertEquals("salma@test.com", customer.getEmail());
            assertSame(newAddress, customer.getAddress());
            verify(repository, times(1)).save(customer);
        }

        @Test
        @DisplayName("Should keep the existing values for null or blank fields (PATCH semantics)")
        void shouldUpdateOnlyProvidedFields() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));

            service.updateCustomer("123", new CustomerUpdateRequest("Jane", "  ", null, null));

            assertEquals("Jane", customer.getFirstname());
            assertEquals("Doe", customer.getLastname());
            assertEquals("john@test.com", customer.getEmail());
            assertSame(address, customer.getAddress());
            verify(repository, times(1)).save(customer);
        }

        @Test
        @DisplayName("Should normalize the new email before storing it")
        void shouldNormalizeEmailOnUpdate() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));
            when(repository.findByEmailIgnoreCase("john@test.com")).thenReturn(Optional.of(customer));

            service.updateCustomer("123", new CustomerUpdateRequest(null, null, "  John@Test.COM ", null));

            assertEquals("john@test.com", customer.getEmail());
            verify(repository, times(1)).save(customer);
        }

        @Test
        @DisplayName("Should throw CustomerNotFoundException when the customer does not exist")
        void shouldThrowWhenCustomerNotFound() {
            when(repository.findById("999")).thenReturn(Optional.empty());

            var exception = assertThrows(CustomerNotFoundException.class,
                    () -> service.updateCustomer("999", new CustomerUpdateRequest("X", "Y", "x@y.com", null)));

            assertTrue(exception.getMsg().contains("No customer found with the provided ID: 999"));
            verify(repository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should reject the update when the new email belongs to another customer")
        void shouldRejectDuplicateEmailOnUpdate() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));
            when(repository.findByEmailIgnoreCase("jane@test.com"))
                    .thenReturn(Optional.of(customer2));

            var exception = assertThrows(CustomerEmailAlreadyExistsException.class,
                    () -> service.updateCustomer("123", new CustomerUpdateRequest("John", "Doe", "jane@test.com", null)));

            assertTrue(exception.getMsg().contains("jane@test.com"));
            assertEquals("john@test.com", customer.getEmail());   // nothing was persisted
            verify(repository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should allow an update that keeps the customer's own email")
        void shouldAllowUpdateWithOwnEmail() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));
            when(repository.findByEmailIgnoreCase("john@test.com"))
                    .thenReturn(Optional.of(customer));   // the email belongs to the customer itself

            service.updateCustomer("123", new CustomerUpdateRequest("John", "Doe", "john@test.com", null));

            assertEquals("john@test.com", customer.getEmail());
            verify(repository, times(1)).save(customer);
        }

        @Test
        @DisplayName("Should skip the email check entirely when no email is provided")
        void shouldSkipEmailCheckWhenEmailNotProvided() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));

            service.updateCustomer("123", new CustomerUpdateRequest("Jane", null, null, null));

            verify(repository, never()).findByEmailIgnoreCase(any());
            verify(repository, times(1)).save(customer);
        }

        @Test
        @DisplayName("Should translate a database DuplicateKeyException into a domain 409 exception")
        void shouldHandleDuplicateKeyRaceOnUpdate() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));
            when(repository.findByEmailIgnoreCase("new@test.com")).thenReturn(Optional.empty());
            when(repository.save(any(Customer.class)))
                    .thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

            var exception = assertThrows(CustomerEmailAlreadyExistsException.class,
                    () -> service.updateCustomer("123", new CustomerUpdateRequest(null, null, "new@test.com", null)));

            assertTrue(exception.getMsg().contains("new@test.com"));
        }
    }

    @Nested
    @DisplayName("deleteCustomer() method")
    class DeleteCustomer {

        @Test
        @DisplayName("Should delete an existing customer")
        void shouldDeleteCustomer() {
            when(repository.existsById("123")).thenReturn(true);

            service.deleteCustomer("123");

            verify(repository, times(1)).deleteById("123");
        }

        @Test
        @DisplayName("Should throw CustomerNotFoundException without deleting when the customer does not exist")
        void shouldThrowWhenCustomerDoesNotExist() {
            when(repository.existsById("999")).thenReturn(false);

            var exception = assertThrows(CustomerNotFoundException.class, () -> service.deleteCustomer("999"));

            assertTrue(exception.getMsg().contains("No customer found with the provided ID: 999"));
            verify(repository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("findAllCustomers() method")
    class FindAllCustomers {

        @Test
        @DisplayName("Should return a page of mapped customers")
        void shouldReturnPagedCustomers() {
            Pageable pageable = PageRequest.of(0, 10);
            when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(customer, customer2), pageable, 2));

            Page<CustomerResponse> result = service.findAllCustomers(pageable);

            assertEquals(2, result.getContent().size());
            assertEquals(2, result.getTotalElements());
            assertEquals("123", result.getContent().get(0).id());
            assertEquals("Jane", result.getContent().get(1).firstname());
            verify(repository, times(1)).findAll(pageable);
        }

        @Test
        @DisplayName("Should return an empty page when there are no customers")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(5, 10);
            when(repository.findAll(pageable)).thenReturn(Page.empty(pageable));

            Page<CustomerResponse> result = service.findAllCustomers(pageable);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findById() method")
    class FindById {

        @Test
        @DisplayName("Should return the mapped customer when found")
        void shouldReturnCustomer() {
            when(repository.findById("123")).thenReturn(Optional.of(customer));

            CustomerResponse result = service.findById("123");

            assertEquals("123", result.id());
            assertEquals("John", result.firstname());
            assertEquals("Doe", result.lastname());
            assertEquals("john@test.com", result.email());
            assertSame(address, result.address());
        }

        @Test
        @DisplayName("Should throw CustomerNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(repository.findById("999")).thenReturn(Optional.empty());

            var exception = assertThrows(CustomerNotFoundException.class, () -> service.findById("999"));

            assertTrue(exception.getMsg().contains("No customer found with the provided ID: 999"));
            verify(mapper, never()).fromCustomer(any(Customer.class));
        }
    }

    @Nested
    @DisplayName("existsById() method")
    class ExistsById {

        @Test
        @DisplayName("Should return true when the customer exists")
        void shouldReturnTrue() {
            when(repository.existsById("123")).thenReturn(true);

            assertTrue(service.existsById("123"));
        }

        @Test
        @DisplayName("Should return false when the customer does not exist")
        void shouldReturnFalse() {
            when(repository.existsById("999")).thenReturn(false);

            assertFalse(service.existsById("999"));
        }
    }
}
