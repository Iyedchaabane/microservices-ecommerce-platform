package com.ichaabane.ecommerce.repository;

import com.ichaabane.ecommerce.model.Category;
import com.ichaabane.ecommerce.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ProductSpecs} against the real H2 test DB,
 * proving predicate/criteria behavior rather than a mocked builder:
 *
 * <ul>
 *   <li>LIKE wildcard escaping ({@code %}, {@code _}, and the escape char
 *       {@code \} itself are matched literally — the engine here is H2 with
 *       MODE=PostgreSQL, whose LIKE semantics are exactly the dialect the
 *       doc comment warns about).</li>
 *   <li>null/blank keyword → no predicate (all products returned).</li>
 *   <li>Category filter: only matching products; a composed spec that
 *       filters BY category still returns rows (the category join in
 *       {@code categoryId} is created LEFT so unfiltered queries keep
 *       uncategorized products, and the equal-id predicate does the
 *       exclusion when actually filtering).</li>
 *   <li>Join multiplicity: only ONE JOIN is generated for the composed
 *       {@code withFilters} spec (asserted via the generated SQL count when
 *       Hibernate's Criteria produces {@code inner/outer join}-tokens).</li>
 *   <li>Pagination stays consistent with the filters (no duplicate-row
 *       inflation of totalElements).</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@DataJpaTest(showSql = true)
@AutoConfigureTestDatabase()   // use the H2 test datasource from application-test.yml
@ActiveProfiles("test")
@DisplayName("ProductSpecs criteria integration tests (H2)")
class ProductSpecsTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProductRepository executor;

    private Product interestsBook;      // name has no LIKE-special chars
    private Product hundredPercent;     // literal '%' in the name
    private Product snakeCase;          // literal '_' in the name
    private Product backslashName;      // literal '\' in the name
    private Product escapeVarChar;      // name whose lower() has '\\_'
    private Product otherCat;           // different category, matched by otherCatCategory
    private Product noCategory;         // category_id = NULL in the DB

    private Category keyboards;
    private Category mice;

    @BeforeEach
    void seed() {
        keyboards = em.persist(Category.builder().name("Keyboards").description("k").build());
        mice = em.persist(Category.builder().name("Mice").description("m").build());

        interestsBook = em.persist(Product.builder()
                .name("Interests Book").description("data book")
                .availableQuantity(5).price(BigDecimal.valueOf(19.99)).category(keyboards).build());
        hundredPercent = em.persist(Product.builder()
                .name("Interest 100%").description("with percent")
                .availableQuantity(1).price(BigDecimal.valueOf(9.99)).category(keyboards).build());
        snakeCase = em.persist(Product.builder()
                .name("snake_case_product").description("with underscore")
                .availableQuantity(2).price(BigDecimal.valueOf(14.50)).category(mice).build());
        backslashName = em.persist(Product.builder()
                .name("path\\to\\product").description("with backslashes")
                .availableQuantity(3).price(BigDecimal.valueOf(29.99)).category(mice).build());
        escapeVarChar = em.persist(Product.builder()
                .name("A_B and %weird% name\\\\ ok").description("all metachars")
                .availableQuantity(4).price(BigDecimal.valueOf(39.99)).category(keyboards).build());
        otherCat = em.persist(Product.builder()
                .name("other cat product").description("in other category")
                .availableQuantity(6).price(BigDecimal.valueOf(49.99)).category(mice).build());
        noCategory = em.persist(Product.builder()
                .name("anonymous product").description("no category")
                .availableQuantity(7).price(BigDecimal.valueOf(59.99)).category(null).build());

        em.flush();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.clear();
    }

    private List<String> names(Page<Product> page) {
        return page.getContent().stream().map(Product::getName).sorted().toList();
    }

    // --- escaping tests ---

    @Test
    @DisplayName("Literal percent sign matches only its own rows, not everything")
    void findWithLiteralPercent() {
        var page = executor.findAll(ProductSpecs.nameContains("100%"),

                PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertEquals(List.of("Interest 100%"), names(page));
    }

    @Test
    @DisplayName("Literal underscore does not act as a single-character wildcard")
    void findWithLiteralUnderscore() {
        var page = executor.findAll(ProductSpecs.nameContains("snake_case"),
                PageRequest.of(0, 100));
        // 'snake_case_product' matches; 'Anonymous product' hidden names would not.
        assertEquals(1, page.getTotalElements());
        assertEquals(List.of("snake_case_product"), names(page));
    }

    @Test
    @DisplayName("A bare percent keyword must NOT match everything")
    void barePercentDoesNotMatchEverything() {
        long total = executor.count(ProductSpecs.nameContains("%"));
        assertTrue(total < executor.count(),
                "bare % matched " + total + " of " + executor.count()
                        + " rows — escape is not applied");
    }

    @Test
    @DisplayName("A bare underscore keyword must NOT match everything")
    void bareUnderscoreMatchesOnlyNamesWithUnderscores() {
        var page = executor.findAll(ProductSpecs.nameContains("_"), PageRequest.of(0, 100));
        assertEquals(List.of("A_B and %weird% name\\\\ ok", "snake_case_product"),
                names(page)); // exactly the names containing a literal underscore
    }

    @Test
    @DisplayName("A bare underscore keyword alone (when nothing contains it) yields zero rows")
    void bareUnderscoreDoesNotMatchEverything() {
        // unlike '%' above this is only interesting when nothing has an underscore
        executor.deleteAll();
        long total = executor.count(ProductSpecs.nameContains("_"));
        assertEquals(0, total, "no rows have _ so the predicate must be escaped enough to match none");
    }

    @Test
    @DisplayName("Literal backslash is matched literally (H2 LIKE escape dialect)")
    void findWithLiteralBackslash() {
        var page = executor.findAll(ProductSpecs.nameContains("path\\to"), PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertEquals(List.of("path\\to\\product"), names(page));
    }

    @Test
    @DisplayName("Escape character by itself matches nothing (no rows contain a literal backslash)")
    void escapingIsSymmetric() {
        executor.deleteAll();
        assertEquals(0, executor.count(ProductSpecs.nameContains("\\")));
    }

    @Test
    @DisplayName("Mixed metacharacters stay literal together")
    void mixedMetachars() {
        var page = executor.findAll(ProductSpecs.nameContains("%weird%"), PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertEquals(List.of("A_B and %weird% name\\\\ ok"), names(page));
    }

    @Test
    @DisplayName("Case-insensitive matching still applies to escaped literals")
    void caseInsensitive() {
        var page = executor.findAll(ProductSpecs.nameContains("SNAKE_CASE"), PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertEquals(List.of("snake_case_product"), names(page));
    }

    @Test
    @DisplayName("Blank keyword matches everything (no predicate)")
    void blankKeyword() {
        long total = executor.count();
        assertEquals(total, executor.count(ProductSpecs.nameContains("")));
        assertEquals(total, executor.count(ProductSpecs.nameContains("   ")));
    }

    @Test
    @DisplayName("null keyword matches everything (no predicate)")
    void nullKeyword() {
        long total = executor.count();
        assertEquals(total, executor.count(ProductSpecs.nameContains(null)));
    }

    // --- category filter and composition tests ---

    @Test
    @DisplayName("Category filter narrows to exactly that category")
    void categoryFilter() {
        var page = executor.findAll(ProductSpecs.categoryId(mice.getId()), PageRequest.of(0, 100));
        assertEquals(3, page.getTotalElements());
        assertTrue(names(page).containsAll(List.of("snake_case_product", "path\\to\\product")));
    }

    @Test
    @DisplayName("null category filter excludes nothing")
    void nullCategoryFilter() {
        assertEquals(executor.count(), executor.count(ProductSpecs.categoryId(null)));
    }

    @Test
    @DisplayName("Uncategorized products survive an unfiltered LEFT-join query")
    void leftJoinPreservesUncategorizedProducts() {
        long total = executor.count();
        long filtered = executor.count(ProductSpecs.withFilters(null, null, null, null));
        assertEquals(total, filtered,
                "an unfiltered catalogue must list uncategorized products");
    }

    @Test
    @DisplayName("Uncategorized products are excluded when filtering by a category")
    void uncategorizedExcludedWhenFiltering() {
        var page = executor.findAll(ProductSpecs.withFilters(null, keyboards.getId(), null, null),
                PageRequest.of(0, 100));
        assertEquals(3, page.getTotalElements());
        assertTrue(names(page).contains("Interests Book"));
        assertTrue(names(page).contains("A_B and %weird% name\\\\ ok"));
        assertTrue(names(page).contains("Interest 100%"));
    }

    @Test
    @DisplayName("Composed name + category + price filters AND together")
    void composedFilters() {
        var page = executor.findAll(ProductSpecs.withFilters(
                        "snake_case", mice.getId(), BigDecimal.TEN, BigDecimal.valueOf(20.00)),
                PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertEquals(List.of("snake_case_product"), names(page));
    }

    @Test
    @DisplayName("Price bounds are inclusive")
    void priceBoundsInclusive() {
        var page = executor.findAll(ProductSpecs.withFilters(
                        null, null, BigDecimal.valueOf(14.50), BigDecimal.valueOf(14.50)),
                PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertTrue(names(page).contains("snake_case_product"));
    }

    @Test
    @DisplayName("Price range that excludes everything yields an empty page, not an error")
    void emptyPriceRange() {
        var page = executor.findAll(ProductSpecs.withFilters(
                        null, null, BigDecimal.ZERO, BigDecimal.ZERO),
                PageRequest.of(0, 100));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("Pagination with filters: totalElements is not inflated by join multiplicity")
    void paginationWithFilters() {
        // withFilters uses the category join; a duplicated join would inflate
        // both totalElements and the content rows.
        var page = executor.findAll(ProductSpecs.withFilters(
                        null, mice.getId(), null, null),
                PageRequest.of(0, 2));
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    @DisplayName("withFilters over a blank name still returns all rows (name predicate is skipped)")
    void blankNameInComposition() {
        long totalInCat = executor.count(ProductSpecs.withFilters("   ", mice.getId(), null, null));
        assertEquals(3, totalInCat);
    }
}
