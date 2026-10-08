package com.ichaabane.ecommerce.mapper;

import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.model.Category;
import com.ichaabane.ecommerce.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ProductMapper Unit Tests")
class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    @Test
    @DisplayName("Should map the request fields and never the identifier")
    void shouldMapRequestFields() {
        var request = new ProductRequest("Book", "A book", 10, BigDecimal.valueOf(15), 3);
        var category = Category.builder().id(3).name("Books").description("Books category").build();

        Product product = mapper.toProduct(request, category);

        assertNull(product.getId());
        assertEquals("Book", product.getName());
        assertEquals("A book", product.getDescription());
        assertEquals(10, product.getAvailableQuantity());
        assertEquals(BigDecimal.valueOf(15), product.getPrice());
        assertEquals(3, product.getCategory().getId());
    }

    @Test
    @DisplayName("Should map a product with its category")
    void shouldMapProductResponse() {
        var category = Category.builder().id(1).name("Books").description("Books category").build();
        var product = Product.builder()
                .id(7).name("Book").description("A book")
                .availableQuantity(10).price(BigDecimal.valueOf(15)).category(category)
                .build();

        ProductResponse response = mapper.toProductResponse(product);

        assertEquals(7, response.id());
        assertEquals("Book", response.name());
        assertEquals(1, response.categoryId());
        assertEquals("Books", response.categoryName());
        assertEquals("Books category", response.categoryDescription());
    }

    @Test
    @DisplayName("Should not fail when the product has no category")
    void shouldHandleNullCategory() {
        var product = Product.builder().id(7).name("Book").availableQuantity(1).build();

        ProductResponse response = mapper.toProductResponse(product);

        assertNull(response.categoryId());
        assertNull(response.categoryName());
        assertNull(response.categoryDescription());
    }

    @Test
    @DisplayName("Should map a purchase response")
    void shouldMapPurchaseResponse() {
        var product = Product.builder()
                .id(7).name("Book").description("A book").price(BigDecimal.valueOf(15))
                .build();

        ProductPurchaseResponse response = mapper.toProductPurchaseResponse(product, 3);

        assertEquals(7, response.productId());
        assertEquals("Book", response.name());
        assertEquals(3, response.quantity());
    }
}