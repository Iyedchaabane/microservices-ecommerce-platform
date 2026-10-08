package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.dto.request.ProductPurchaseRequest;
import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import com.ichaabane.ecommerce.mapper.ProductMapper;
import com.ichaabane.ecommerce.model.Category;
import com.ichaabane.ecommerce.model.Product;
import com.ichaabane.ecommerce.repository.CategoryRepository;
import com.ichaabane.ecommerce.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Boundary Unit Tests")
class ProductServiceBoundaryTest {

    @Mock
    private ProductRepository repository;
    @Mock
    private CategoryRepository categoryRepository;

    private final ProductMapper mapper = new ProductMapper();

    private ProductService service;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository, categoryRepository, mapper);
        category = Category.builder().id(1).name("Books").description("Books category").build();
    }

    private Product product(int id, double quantity) {
        return Product.builder()
                .id(id).name("P" + id).description("d").availableQuantity(quantity)
                .price(BigDecimal.valueOf(10)).category(category)
                .build();
    }

    @Nested
    @DisplayName("purchaseProducts() boundaries")
    class PurchaseBoundaries {

        @Test
        @DisplayName("Should allow buying the exact remaining stock (down to zero)")
        void shouldAllowExactStock() {
            var book = product(1, 5);
            when(repository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(book));

            var result = service.purchaseProducts(List.of(new ProductPurchaseRequest(1, 5)));

            assertEquals(0, book.getAvailableQuantity());
            assertEquals(5, result.get(0).quantity());
        }

        @Test
        @DisplayName("Should decrement by a fractional quantity")
        void shouldSupportFractionalQuantity() {
            var book = product(1, 5.5);
            when(repository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(book));

            service.purchaseProducts(List.of(new ProductPurchaseRequest(1, 2.5)));

            assertEquals(3.0, book.getAvailableQuantity());
        }

        @Test
        @DisplayName("Should refuse a purchase when the stock is zero")
        void shouldRefuseWhenStockIsZero() {
            when(repository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(product(1, 0)));

            var exception = org.junit.jupiter.api.Assertions.assertThrows(ProductPurchaseException.class,
                    () -> service.purchaseProducts(List.of(new ProductPurchaseRequest(1, 1))));

            assertEquals("Insufficient stock quantity for product with ID:: 1", exception.getMessage());
        }

        @Test
        @DisplayName("Should look products up by ascending, de-duplicated id")
        void shouldLookUpByIdsInAscendingOrder() {
            var book = product(1, 10);
            var pen = product(3, 10);
            when(repository.findAllByIdInOrderById(List.of(1, 3))).thenReturn(List.of(book, pen));

            service.purchaseProducts(List.of(
                    new ProductPurchaseRequest(3, 1),
                    new ProductPurchaseRequest(1, 1),
                    new ProductPurchaseRequest(3, 1)));

            verify(repository, times(1)).findAllByIdInOrderById(List.of(1, 3));
            assertEquals(9, book.getAvailableQuantity());
            assertEquals(8, pen.getAvailableQuantity());
        }

        @Test
        @DisplayName("Should explain an unknown product")
        void shouldExplainUnknownProduct() {
            when(repository.findAllByIdInOrderById(List.of(1, 2))).thenReturn(List.of(product(1, 5)));

            var exception = org.junit.jupiter.api.Assertions.assertThrows(ProductPurchaseException.class,
                    () -> service.purchaseProducts(List.of(
                            new ProductPurchaseRequest(1, 1),
                            new ProductPurchaseRequest(2, 1))));

            assertEquals("One or more products does not exist", exception.getMessage());
            verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
        }

        @Test
        @DisplayName("Should return one response per distinct product with its price")
        void shouldReturnOneResponsePerProduct() {
            when(repository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(product(1, 10)));

            var result = service.purchaseProducts(List.of(new ProductPurchaseRequest(1, 2)));

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).productId());
            assertEquals(BigDecimal.valueOf(10), result.get(0).price());
        }
    }

    @Nested
    @DisplayName("read/create edges")
    class ReadCreateEdges {

        @Test
        @DisplayName("Should explain an unknown category when creating a product")
        void shouldExplainUnknownCategory() {
            when(categoryRepository.findById(99)).thenReturn(Optional.empty());

            var exception = org.junit.jupiter.api.Assertions.assertThrows(EntityNotFoundException.class,
                    () -> service.createProduct(new ProductRequest("Book", "d", 1, BigDecimal.ONE, 99)));

            assertTrue(exception.getMessage().contains("Category not found with ID:: 99"));
            verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
        }

        @Test
        @DisplayName("Should explain an unknown product on findById")
        void shouldExplainUnknownProductOnFindById() {
            when(repository.findById(99)).thenReturn(Optional.empty());

            var exception = org.junit.jupiter.api.Assertions.assertThrows(EntityNotFoundException.class,
                    () -> service.findById(99));

            assertTrue(exception.getMessage().contains("Product not found with ID:: 99"));
        }

        @Test
        @DisplayName("Should return an empty list for an empty catalogue")
        void shouldReturnEmptyCatalogue() {
            when(repository.findAll()).thenReturn(List.of());

            assertTrue(service.findAll().isEmpty());
        }

        @Test
        @DisplayName("Should map every product of a multi-entry catalogue")
        void shouldMapEveryProduct() {
            when(repository.findAll()).thenReturn(List.of(product(1, 3), product(2, 4), product(3, 5)));

            List<ProductResponse> result = service.findAll();

            assertEquals(3, result.size());
            assertEquals("P1", result.get(0).name());
            assertEquals("P3", result.get(2).name());
        }
    }
}
