package com.ichaabane.ecommerce.controller;

import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.exception.CustomerEmailAlreadyExistsException;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
import com.ichaabane.ecommerce.handler.GlobalExceptionHandler;
import com.ichaabane.ecommerce.model.Address;
import com.ichaabane.ecommerce.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerController Unit Tests")
class CustomerControllerTest {

    @Mock
    private CustomerService service;

    @InjectMocks
    private CustomerController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/customers")
    class CreateCustomer {

        @Test
        @DisplayName("Should return 201 with a Location header and the generated id for a valid body")
        void shouldCreateCustomer() throws Exception {
            when(service.createCustomer(any())).thenReturn("generated-id");

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/customers/generated-id"))
                    .andExpect(content().string("generated-id"));

            verify(service, times(1)).createCustomer(any());
        }

        @Test
        @DisplayName("Should return 400 when the firstname is blank")
        void shouldRejectBlankFirstname() throws Exception {
            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"  \",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.firstname").exists());

            verify(service, never()).createCustomer(any());
        }

        @Test
        @DisplayName("Should return 400 when the email is not a valid address")
        void shouldRejectInvalidEmail() throws Exception {
            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists());

            verify(service, never()).createCustomer(any());
        }

        @Test
        @DisplayName("Should return 400 when the lastname is missing")
        void shouldRejectMissingLastname() throws Exception {
            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"John\",\"email\":\"john@doe.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.lastname").exists());
        }

        @Test
        @DisplayName("Should ignore a client-supplied id (not part of the create DTO)")
        void shouldIgnoreClientSuppliedId() throws Exception {
            when(service.createCustomer(any())).thenReturn("generated-id");

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"hacked-id\",\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}"))
                    .andExpect(status().isCreated());

            // The body is deserialized into a DTO without an id field: nothing to assert
            // on the payload itself, but creation must not be rejected or altered.
            verify(service, times(1)).createCustomer(any());
        }

        @Test
        @DisplayName("Should return 409 when the email already exists")
        void shouldReturnConflictOnDuplicateEmail() throws Exception {
            when(service.createCustomer(any()))
                    .thenThrow(new CustomerEmailAlreadyExistsException("Customer with email john@doe.com already exists"));

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(content().string("Customer with email john@doe.com already exists"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/customers/{id}")
    class UpdateCustomer {

        @Test
        @DisplayName("Should return 202 when the update succeeds")
        void shouldUpdateCustomer() throws Exception {
            mockMvc.perform(put("/api/v1/customers/123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}"))
                    .andExpect(status().isAccepted());

            verify(service, times(1)).updateCustomer(eq("123"), any());
        }

        @Test
        @DisplayName("Should accept an empty body (PATCH semantics: nothing to update)")
        void shouldAcceptEmptyBody() throws Exception {
            mockMvc.perform(put("/api/v1/customers/123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isAccepted());

            verify(service, times(1)).updateCustomer(eq("123"), any());
        }

        @Test
        @DisplayName("Should return 400 when the provided email is invalid")
        void shouldRejectInvalidEmail() throws Exception {
            mockMvc.perform(put("/api/v1/customers/123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists());

            verify(service, never()).updateCustomer(any(), any());
        }

        @Test
        @DisplayName("Should return 404 when the customer does not exist")
        void shouldReturn404WhenCustomerMissing() throws Exception {
            org.mockito.Mockito.doThrow(new CustomerNotFoundException("No customer found with the provided ID: 999"))
                    .when(service).updateCustomer(eq("999"), any());

            mockMvc.perform(put("/api/v1/customers/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"John\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 409 when the new email belongs to another customer")
        void shouldReturn409OnDuplicateEmail() throws Exception {
            org.mockito.Mockito.doThrow(new CustomerEmailAlreadyExistsException("Customer with email jane@doe.com already exists"))
                    .when(service).updateCustomer(eq("123"), any());

            mockMvc.perform(put("/api/v1/customers/123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"jane@doe.com\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers")
    class FindAll {

        @Test
        @DisplayName("Should return a page of customers with navigation metadata")
        void shouldReturnPagedCustomers() throws Exception {
            var address = new Address("Main St", "12", "75001");
            var page = new PageImpl<>(
                    List.of(
                            new CustomerResponse("1", "John", "Doe", "john@doe.com", address),
                            new CustomerResponse("2", "Jane", "Smith", "jane@doe.com", null)
                    ),
                    PageRequest.of(0, 2),
                    5
            );
            when(service.findAllCustomers(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/customers")
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value("1"))
                    .andExpect(jsonPath("$.content[0].address.street").value("Main St"))
                    .andExpect(jsonPath("$.content[1].firstname").value("Jane"))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.number").value(0))
                    .andExpect(jsonPath("$.size").value(2));
        }

        @Test
        @DisplayName("Should apply default pagination (page 0, size 10)")
        void shouldApplyDefaultPagination() throws Exception {
            when(service.findAllCustomers(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get("/api/v1/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            verify(service).findAllCustomers(PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("Should clamp an out-of-range page size instead of failing")
        void shouldClampInvalidPagination() throws Exception {
            when(service.findAllCustomers(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

            mockMvc.perform(get("/api/v1/customers")
                            .param("page", "-5")
                            .param("size", "10000"))
                    .andExpect(status().isOk());

            verify(service).findAllCustomers(PageRequest.of(0, 100));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/exists/{id}")
    class ExistsById {

        @Test
        @DisplayName("Should return true when the customer exists")
        void shouldReturnTrue() throws Exception {
            when(service.existsById("123")).thenReturn(true);

            mockMvc.perform(get("/api/v1/customers/exists/123"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Should return false when the customer does not exist")
        void shouldReturnFalse() throws Exception {
            when(service.existsById("999")).thenReturn(false);

            mockMvc.perform(get("/api/v1/customers/exists/999"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/{id}")
    class FindById {

        @Test
        @DisplayName("Should return the customer when found")
        void shouldReturnCustomer() throws Exception {
            when(service.findById("123"))
                    .thenReturn(new CustomerResponse("123", "John", "Doe", "john@doe.com", null));

            mockMvc.perform(get("/api/v1/customers/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("123"))
                    .andExpect(jsonPath("$.email").value("john@doe.com"));
        }

        @Test
        @DisplayName("Should return 404 with the not-found message when the customer is missing")
        void shouldReturn404WhenNotFound() throws Exception {
            when(service.findById("999"))
                    .thenThrow(new CustomerNotFoundException("No customer found with the provided ID: 999"));

            mockMvc.perform(get("/api/v1/customers/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string("No customer found with the provided ID: 999"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/customers/{id}")
    class DeleteCustomer {

        @Test
        @DisplayName("Should return 204 and delegate the deletion")
        void shouldDeleteCustomer() throws Exception {
            mockMvc.perform(delete("/api/v1/customers/123"))
                    .andExpect(status().isNoContent());

            verify(service, times(1)).deleteCustomer("123");
        }

        @Test
        @DisplayName("Should return 404 when the customer does not exist")
        void shouldReturn404WhenCustomerMissing() throws Exception {
            org.mockito.Mockito.doThrow(new CustomerNotFoundException("No customer found with the provided ID: 999"))
                    .when(service).deleteCustomer("999");

            mockMvc.perform(delete("/api/v1/customers/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
