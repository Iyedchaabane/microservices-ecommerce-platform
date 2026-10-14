package com.ichaabane.ecommerce.controller;

import com.ichaabane.ecommerce.dto.response.PageResponse;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import com.ichaabane.ecommerce.exception.ResourceNotFoundException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        @DisplayName("Should return 201 with Location and the created id for a valid product")
        void shouldCreateProduct() throws Exception {
            when(service.createProduct(any())).thenReturn(7);

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":10,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/products/7"))
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
        @DisplayName("Should return 400 when the quantity is negative (zero is valid)")
        void shouldRejectNegativeQuantity() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":-5,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.availableQuantity").exists());
        }

        /**
         * A missing availableQuantity is a client error, not a server error.
         * Before the field was made @NotNull, a null slipped past @Min (which
         * ignores nulls) and NPE'd on unboxing to the primitive int entity
         * field, surfacing as a 500.
         */
        @Test
        @DisplayName("Should return 400 when the quantity is missing (null)")
        void shouldRejectNullQuantity() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":null,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.availableQuantity").exists());
        }

        @Test
        @DisplayName("Should accept a zero quantity (registered awaiting restock)")
        void shouldAcceptZeroQuantity() throws Exception {
            when(service.createProduct(any())).thenReturn(3);

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Book\",\"description\":\"A book\",\"availableQuantity\":0,\"price\":15.5,\"categoryId\":1}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/products/3"));
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
        @DisplayName("Should reject a non-positive quantity at the service boundary")
        void shouldRejectNonPositiveQuantity() throws Exception {
            // Bean Validation runs in the real MVC stack; the standalone MockMvc here
            // proves the service rejects it regardless of the caller's validation.
            when(service.purchaseProducts(anyList()))
                    .thenThrow(new ProductPurchaseException("Quantity must be positive for product with ID:: 1"));

            mockMvc.perform(post("/api/v1/products/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"productId\":1,\"quantity\":0}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value("Quantity must be positive for product with ID:: 1"));
        }

        @Test
        @DisplayName("Should reject a missing product id at the service boundary")
        void shouldRejectMissingProductId() throws Exception {
            when(service.purchaseProducts(anyList()))
                    .thenThrow(new ProductPurchaseException("Product is mandatory"));

            mockMvc.perform(post("/api/v1/products/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"quantity\":1}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value("Product is mandatory"));
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
        @DisplayName("Should map an unknown product to 404 via the controller advice")
        void shouldReturn404WhenNotFound() throws Exception {
            when(service.findById(99)).thenThrow(new ResourceNotFoundException("Product", 99));

            mockMvc.perform(get("/api/v1/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$").value("Product not found with ID:: 99"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products (paginated)")
    class FindAll {

        @Test
        @DisplayName("Should return a paginated envelope with defaults")
        void shouldReturnAllProducts() throws Exception {
            var page = new PageResponse<>(
                    List.of(new ProductResponse(1, "Book", "A book", 10, BigDecimal.ONE, 1, "Cat", "d"),
                            new ProductResponse(2, "Pen", "A pen", 5, BigDecimal.TEN, 1, "Cat", "d")),
                    0, 20, 2, 1, true);
            when(service.findAll(0, 20, null, null)).thenReturn(page);

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[1].name").value("Pen"))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("Should pass explicit page, size, sort and order to the service")
        void shouldPassPaginationParameters() throws Exception {
            when(service.findAll(2, 5, "price", "desc"))
                    .thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 1, true));

            mockMvc.perform(get("/api/v1/products")
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "price")
                            .param("order", "desc"))
                    .andExpect(status().isOk());

            verify(service).findAll(2, 5, "price", "desc");
        }

        /**
         * Parameter-bound constraints (@Min page/size) are enforced by Spring's
         * method validation in a real MVC stack; in stand-alone MockMvc the
         * equivalent guarantee is the service-level check, so we stub it.
         */
        @Test
        @DisplayName("Should return 400 for a negative page index or oversized page")
        void shouldRejectInvalidPaginationParameters() throws Exception {
            when(service.findAll(-1, 20, null, null))
                    .thenThrow(new IllegalArgumentException("Page index must be >= 0"));
            when(service.findAll(0, 1000, null, null))
                    .thenThrow(new IllegalArgumentException("Page size must be <= 100"));

            mockMvc.perform(get("/api/v1/products").param("page", "-1"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/v1/products").param("size", "1000"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * An unsupported sort field is rejected by the service whitelist; the
         * stand-alone MockMvc maps the IllegalArgumentException through the advice.
         */
        @Test
        @DisplayName("Should return 400 when requesting an unsupported sort field")
        void shouldRejectUnsupportedSortField() throws Exception {
            when(service.findAll(0, 20, "description", null))
                    .thenThrow(new IllegalArgumentException("Unsupported sort field: description"));

            mockMvc.perform(get("/api/v1/products").param("sort", "description"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should serve the search endpoint with filters")
        void shouldServeSearchEndpoint() throws Exception {
            when(service.search("book", 1, BigDecimal.ONE, BigDecimal.TEN, 0, 20, null, null))
                    .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

            mockMvc.perform(get("/api/v1/products/search")
                            .param("name", "book")
                            .param("categoryId", "1")
                            .param("priceFrom", "1")
                            .param("priceTo", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }
}
