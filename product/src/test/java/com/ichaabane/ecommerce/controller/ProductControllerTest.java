package com.ichaabane.ecommerce.controller;

import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import com.ichaabane.ecommerce.handler.GlobalExceptionHandler;
import com.ichaabane.ecommerce.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController Unit Tests")
class ProductControllerTest {

    @Mock
    private ProductService service;

    @InjectMocks
    private ProductController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/products")
    class CreateProduct {

        @Test
        @DisplayName("Should return 200 and the created id for a valid product")
        void shouldCreateProduct() throws Exception {
            when(service.createProduct(any())).thenReturn(7);

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":10,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(7));
        }

        @Test
        @DisplayName("Should return 400 when the name is blank")
        void shouldRejectBlankName() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"  \",\"description\":\"A book\",\"availableQuantity\":10,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());

            verify(service, never()).createProduct(any());
        }

        @Test
        @DisplayName("Should return 400 when the quantity is not positive")
        void shouldRejectNonPositiveQuantity() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":0,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.availableQuantity").exists());
        }

        @Test
        @DisplayName("Should return 400 when the price is negative")
        void shouldRejectNegativePrice() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":10,\"price\":-1,\"categoryId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.price").exists());
        }

        @Test
        @DisplayName("Should return 400 when the category is missing")
        void shouldRejectMissingCategory() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":10,\"price\":15.5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.categoryId").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/products/purchase")
    class PurchaseProducts {

        @Test
        @DisplayName("Should return 200 with the purchased lines for a valid body")
        void shouldPurchaseProducts() throws Exception {
            when(service.purchaseProducts(anyList())).thenReturn(List.of());

            mockMvc.perform(post("/api/v1/products/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"productId\":1,\"quantity\":2}]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Should return 400 when a requested quantity is not positive")
        void shouldRejectNonPositiveQuantity() throws Exception {
            // Container element constraints are enforced by Spring's method validation,
            // which returns 400 without invoking the service.
            mockMvc.perform(post("/api/v1/products/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"productId\":1,\"quantity\":0}]"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).purchaseProducts(anyList());
        }

        @Test
        @DisplayName("Should return 400 when a product id is missing")
        void shouldRejectMissingProductId() throws Exception {
            mockMvc.perform(post("/api/v1/products/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"quantity\":1}]"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).purchaseProducts(anyList());
        }

        @Test
        @DisplayName("Should surface a purchase business error as 400 with its message")
        void shouldSurfacePurchaseException() throws Exception {
            when(service.purchaseProducts(anyList()))
                    .thenThrow(new ProductPurchaseException("Insufficient stock quantity for product with ID:: 1"));

            mockMvc.perform(post("/api/v1/products/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"productId\":1,\"quantity\":99}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value("Insufficient stock quantity for product with ID:: 1"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class FindById {

        @Test
        @DisplayName("Should return the product when it exists")
        void shouldReturnProduct() throws Exception {
            when(service.findById(1)).thenReturn(new ProductResponse(
                    1, "Book", "A book", 10, BigDecimal.valueOf(15), 2, "Books", "Books category"));

            mockMvc.perform(get("/api/v1/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.categoryName").value("Books"));
        }

        @Test
        @DisplayName("Should map an unknown product to 400 via the controller advice")
        void shouldReturn400WhenNotFound() throws Exception {
            when(service.findById(99)).thenThrow(new EntityNotFoundException("Product not found with ID:: 99"));

            mockMvc.perform(get("/api/v1/products/99"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value("Product not found with ID:: 99"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class FindAll {

        @Test
        @DisplayName("Should return every product")
        void shouldReturnAllProducts() throws Exception {
            when(service.findAll()).thenReturn(List.of(
                    new ProductResponse(1, "Book", "A book", 10, BigDecimal.ONE, 1, "Cat", "d"),
                    new ProductResponse(2, "Pen", "A pen", 5, BigDecimal.TEN, 1, "Cat", "d")));

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].name").value("Pen"));
        }

        @Test
        @DisplayName("Should return an empty array when the catalogue is empty")
        void shouldReturnEmptyArray() throws Exception {
            when(service.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
