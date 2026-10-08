package com.ichaabane.ecommerce.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Product model Unit Tests")
class ProductModelTest {

    @Nested
    @DisplayName("Product entity")
    class ProductTests {

        @Test
        @DisplayName("Should build a product with the builder")
        void shouldBuild() {
            var category = Category.builder().id(1).name("Books").build();

            var product = Product.builder()
                    .id(7).name("Book").description("A book")
                    .availableQuantity(10).price(BigDecimal.valueOf(15)).category(category)
                    .build();

            assertEquals(7, product.getId());
            assertEquals("Book", product.getName());
            assertEquals("A book", product.getDescription());
            assertEquals(10, product.getAvailableQuantity());
            assertEquals(BigDecimal.valueOf(15), product.getPrice());
            assertSame(category, product.getCategory());
        }

        @Test
        @DisplayName("Should initialise to nulls and allow mutation")
        void shouldAllowMutation() {
            var product = new Product();

            assertNull(product.getId());
            product.setAvailableQuantity(4.5);
            product.setName("Pen");

            assertEquals(4.5, product.getAvailableQuantity());
            assertEquals("Pen", product.getName());
        }

        @Test
        @DisplayName("Should use the all-args constructor")
        void shouldUseAllArgsConstructor() {
            var category = Category.builder().id(2).build();

            var product = new Product(1, "P", "d", 3, BigDecimal.TEN, category);

            assertEquals(1, product.getId());
            assertSame(category, product.getCategory());
        }
    }

    @Nested
    @DisplayName("Category entity")
    class CategoryTests {

        @Test
        @DisplayName("Should build a category with the builder")
        void shouldBuild() {
            var category = Category.builder().id(1).name("Books").description("Books category").build();

            assertEquals(1, category.getId());
            assertEquals("Books", category.getName());
            assertEquals("Books category", category.getDescription());
        }

        @Test
        @DisplayName("Should expose the products collection")
        void shouldExposeProducts() {
            var product = Product.builder().id(1).name("Book").build();
            var category = Category.builder().id(1).name("Books").products(List.of(product)).build();

            assertEquals(1, category.getProducts().size());
            assertSame(product, category.getProducts().get(0));
        }

        @Test
        @DisplayName("Should initialise to nulls and allow mutation")
        void shouldAllowMutation() {
            var category = new Category();

            assertNull(category.getId());
            category.setName("Mice");
            assertEquals("Mice", category.getName());
        }
    }
}
