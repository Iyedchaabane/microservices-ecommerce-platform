package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.dto.request.ProductPurchaseRequest;
import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private CategoryRepository categoryRepository;

    private final ProductMapper mapper = new ProductMapper();

    private ProductService service;

    private Category category;


    @BeforeEach
    void setUp() {
        // Argument order = field declaration order in ProductService (@RequiredArgsConstructor)
        service = new ProductService(repository, categoryRepository, mapper);
        category = Category.builder().id(1).name("Books").description("Books category").build();
    }

    private Product product(int id, String name, double quantity, double price) {
        return Product.builder()
                .id(id)
                .name(name)
                .description(name + " description")
                .availableQuantity(quantity)
                .price(BigDecimal.valueOf(price))
                .category(category)
                .build();
    }

    @Nested
    @DisplayName("createProduct() method")
    class CreateProductTests {

        @Test
        @DisplayName("Should persist the product with its category and return the generated id")
        void shouldCreateProduct() {
            var request = new ProductRequest("Book", "A book", 10, BigDecimal.valueOf(15), 1);
            when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
            when(repository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId(42);
                return saved;
            });

            Integer id = service.createProduct(request);

            assertEquals(42, id);
            var captor = ArgumentCaptor.forClass(Product.class);
            verify(repository, times(1)).save(captor.capture());
            assertSame(category, captor.getValue().getCategory());
            assertEquals("Book", captor.getValue().getName());
        }

        @Test
        @DisplayName("Should reject an unknown category and not persist anything")
        void shouldRejectUnknownCategory() {
            var request = new ProductRequest("Book", "A book", 10, BigDecimal.valueOf(15), 99);
            when(categoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> service.createProduct(request));

            verify(repository, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("findById() and findAll() methods")
    class ReadTests {

        @Test
        @DisplayName("Should return the mapped product when it exists")
        void shouldReturnProduct() {
            when(repository.findById(1)).thenReturn(Optional.of(product(1, "Book", 10, 15)));

            ProductResponse response = service.findById(1);

            assertEquals(1, response.id());
            assertEquals("Book", response.name());
            assertEquals("Books", response.categoryName());
        }

        @Test
        @DisplayName("Should throw when the product does not exist")
        void shouldThrowWhenNotFound() {
            when(repository.findById(99)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> service.findById(99));
        }

        @Test
        @DisplayName("Should map every product of the catalogue")
        void shouldReturnAllProducts() {
            when(repository.findAll()).thenReturn(List.of(
                    product(1, "Book", 10, 15),
                    product(2, "Pen", 5, 2)
            ));

            List<ProductResponse> products = service.findAll();

            assertEquals(2, products.size());
            assertEquals("Book", products.get(0).name());
            assertEquals("Pen", products.get(1).name());
        }
    }

    @Nested
    @DisplayName("purchaseProducts() method")
    class PurchaseTests {

        @Test
        @DisplayName("Should decrement the stock and return the purchased lines")
        void shouldPurchaseProducts() {
            var book = product(1, "Book", 10, 15);
            var pen = product(2, "Pen", 5, 2);
            var request = List.of(
                    new ProductPurchaseRequest(1, 3),
                    new ProductPurchaseRequest(2, 1)
            );
            when(repository.findAllByIdInOrderById(List.of(1, 2))).thenReturn(List.of(book, pen));

            List<ProductPurchaseResponse> purchased = service.purchaseProducts(request);

            assertEquals(2, purchased.size());
            assertEquals(7, book.getAvailableQuantity());
            assertEquals(4, pen.getAvailableQuantity());
            assertEquals(3, purchased.get(0).quantity());
            verify(repository, times(2)).save(any(Product.class));
        }

        @Test
        @DisplayName("Should aggregate repeated lines for the same product")
        void shouldAggregateDuplicateLines() {
            var book = product(1, "Book", 10, 15);
            var request = List.of(
                    new ProductPurchaseRequest(1, 2),
                    new ProductPurchaseRequest(1, 3)
            );
            when(repository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(book));

            List<ProductPurchaseResponse> purchased = service.purchaseProducts(request);

            assertEquals(1, purchased.size());
            assertEquals(5, purchased.get(0).quantity());
            assertEquals(5, book.getAvailableQuantity());
        }

        @Test
        @DisplayName("Should give each product its own quantity when duplicates are mixed with other products")
        void shouldPairQuantitiesByProductWhenDuplicatesAreMixed() {
            var book = product(1, "Book", 10, 15);
            var pen = product(2, "Pen", 10, 2);
            var request = List.of(
                    new ProductPurchaseRequest(1, 1),
                    new ProductPurchaseRequest(1, 2),
                    new ProductPurchaseRequest(2, 4)
            );
            when(repository.findAllByIdInOrderById(List.of(1, 2))).thenReturn(List.of(book, pen));

            List<ProductPurchaseResponse> purchased = service.purchaseProducts(request);

            assertEquals(3, purchased.get(0).quantity());
            assertEquals(4, purchased.get(1).quantity());
            assertEquals(7, book.getAvailableQuantity());
            assertEquals(6, pen.getAvailableQuantity());
        }

        @Test
        @DisplayName("Should throw when one of the products does not exist")
        void shouldThrowWhenProductMissing() {
            var request = List.of(
                    new ProductPurchaseRequest(1, 1),
                    new ProductPurchaseRequest(3, 1)
            );
            when(repository.findAllByIdInOrderById(List.of(1, 3)))
                    .thenReturn(List.of(product(1, "Book", 10, 15)));

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verify(repository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw when the requested quantity exceeds the stock")
        void shouldThrowWhenInsufficientStock() {
            var request = List.of(new ProductPurchaseRequest(1, 100));
            when(repository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(product(1, "Book", 10, 15)));

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verify(repository, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("purchaseProducts() input validation")
    class PurchaseValidationTests {

        @Test
        @DisplayName("Should reject a negative quantity (it would otherwise ADD stock)")
        void shouldRejectNegativeQuantity() {
            var request = List.of(new ProductPurchaseRequest(1, -5));

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Should reject a zero quantity")
        void shouldRejectZeroQuantity() {
            var request = List.of(new ProductPurchaseRequest(1, 0));

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Should reject a NaN quantity")
        void shouldRejectNaNQuantity() {
            var request = List.of(new ProductPurchaseRequest(1, Double.NaN));

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Should reject a missing product id")
        void shouldRejectNullProductId() {
            var request = List.of(new ProductPurchaseRequest(null, 1));

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Should reject a null element in the request")
        void shouldRejectNullElement() {
            var request = Arrays.asList(new ProductPurchaseRequest(1, 1), null);

            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(request));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Should reject an empty or null request")
        void shouldRejectEmptyOrNullRequest() {
            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(List.of()));
            assertThrows(ProductPurchaseException.class, () -> service.purchaseProducts(null));

            verifyNoInteractions(repository);
        }
    }
}