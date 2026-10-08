package com.ichaabane.ecommerce.mapper;

import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.model.Category;
import com.ichaabane.ecommerce.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ProductMapper complementary Unit Tests")
class ProductMapperExtraTest {

    private final ProductMapper mapper = new ProductMapper();

    @Test
    @DisplayName("Should map every field of a purchase response, including price and description")
    void shouldMapFullPurchaseResponse() {
        var product = Product.builder()
                .id(9).name("Book").description("A book")
                .availableQuantity(10).price(BigDecimal.valueOf(12.5))
                .build();

        ProductPurchaseResponse response = mapper.toProductPurchaseResponse(product, 4);

        assertEquals(9, response.productId());
        assertEquals("Book", response.name());
        assertEquals("A book", response.description());
        assertEquals(BigDecimal.valueOf(12.5), response.price());
        assertEquals(4, response.quantity());
    }

    @Test
    @DisplayName("Should carry the stock and price into the product response")
    void shouldCarryStockAndPrice() {
        var category = Category.builder().id(2).name("Mice").description("Mice category").build();
        var product = Product.builder()
                .id(3).name("Mouse").description("A mouse")
                .availableQuantity(7).price(BigDecimal.valueOf(19.99)).category(category)
                .build();

        ProductResponse response = mapper.toProductResponse(product);

        assertEquals(7, response.availableQuantity());
        assertEquals(BigDecimal.valueOf(19.99), response.price());
        assertEquals(2, response.categoryId());
    }

    @Test
    @DisplayName("Should take the category data from the nested entity")
    void shouldMapNestedCategory() {
        var category = Category.builder().id(5).name("Keyboards").description("Kbd").build();
        var product = Product.builder().id(1).name("Kbd").availableQuantity(1).category(category).build();

        ProductResponse response = mapper.toProductResponse(product);

        assertEquals(5, response.categoryId());
        assertEquals("Keyboards", response.categoryName());
        assertEquals("Kbd", response.categoryDescription());
    }
}
