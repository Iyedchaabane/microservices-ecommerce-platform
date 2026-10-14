package com.ichaabane.ecommerce.repository;

import com.ichaabane.ecommerce.model.Category;
import com.ichaabane.ecommerce.model.Product;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProductSpecs {

    /**
     * The single escape character used in every LIKE pattern emitted by
     * {@link #nameContains}. Declared once because the SAME character must be
     * passed to CriteriaBuilder's {@code like(..., escapeChar)} and used when
     * escaping the keyword — different constants there would silently mismatch.
     */
    private static final char LIKE_ESCAPE_CHAR = '\\';

    private ProductSpecs() {
    }

    /**
     * Case-insensitive substring match on {@code product.name}.
     *
     * <p><strong>Escaping.</strong> A plain {@code lower(name) LIKE %kw%}
     * binds the keyword as a parameter — it is immune to SQL injection — but
     * the keyword still reaches the LIKE engine raw, so a client searching
     * for a literal {@code %}, {@code _} or {@code \} gets wildcard semantics
     * instead ({@code 100%} matches "100x", {@code a_b} matches "axb").
     * Databases differ in their default escape character (PostgreSQL:
     * none by default; H2/MODE=PostgreSQL honors the ESCAPE clause), so the
     * predicate never relies on the default: the keyword is pre-escaped here
     * and the SAME {@link #LIKE_ESCAPE_CHAR} is passed explicitly to
     * {@code cb.like(..., escape)}. Escaping order matters: the escape char
     * itself must be doubled FIRST, otherwise a later step would also escape
     * the doubling backslashes we just wrote. The literal-{@code \} search
     * case is covered by {@code ProductSpecsTest} against H2, the engine
     * whose LIKE dialect differs from PostgreSQL's.</p>
     *
     * <p>Behavior on edge input:</p>
     * <ul>
     *   <li>{@code null}, empty or blank keyword → {@code cb.conjunction()}
     *       (no predicate); a blank search must not match
     *       {@code %%%}.</li>
     *   <li>Case-insensitivity via {@code cb.lower} on BOTH sides; the
     *       keyword is lower-cased after trimming.</li>
     *   <li>Leading/trailing whitespace is trimmed so " mouse " finds the
     *       same rows as "mouse".</li>
     * </ul>
     *
     * @param rawKeyword the client-supplied search text; treated literally
     *        (with {@code %}, {@code _} and {@code \} escaped)
     */
    public static Specification<Product> nameContains(String rawKeyword) {
        return (root, query, cb) -> {
            if (rawKeyword == null || rawKeyword.isBlank()) {
                return cb.conjunction();
            }
            String keyword = rawKeyword.trim().toLowerCase();
            String escaped = escapeLike(keyword);
            String pattern = "%" + escaped + "%";
            return cb.like(cb.lower(cb.trim(root.get("name"))), pattern, LIKE_ESCAPE_CHAR);
        };
    }

    /**
     * Escapes the three LIKE metacharacters in {@code keyword} using
     * {@link #LIKE_ESCAPE_CHAR}, in the order that keeps the doubling
     * correct.
     */
    private static String escapeLike(String keyword) {
        StringBuilder sb = new StringBuilder(keyword.length());
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == LIKE_ESCAPE_CHAR || c == '%' || c == '_') {
                sb.append(LIKE_ESCAPE_CHAR);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Shared accessor for the {@code product.category} association.
     *
     * <p><strong>Why LEFT join.</strong> The plain {@code findAll} page
     * (no filters) must return products whose {@code category_id} is NULL,
     * and map/cat responses are built via
     * {@code product.getCategory() != null} — anonymous products are part of
     * the catalogue. An INNER join here would drop them from ANY query that
     * happens to resolve this spec path. LEFT join keeps those rows visible
     * whenever the filter itself is not applied, while still behaving as an
     * INNER match when {@link #categoryId(Integer)} actually filters (the
     * {@code category.id = :id} predicate discards NULL-join rows anyway).</p>
     *
     * <p><strong>Why a shared helper.</strong> JPA correlates repeated joins
     * of the same path/attribute into one SQL join, so correctness does not
     * depend on this helper — but routing both the filter spec and any future
     * spec that needs the category row through ONE accessor keeps that
     * correlation explicitly intentional, gives a single place to document
     * the join type and avoids surprises if a provider chooses not to
     * correlate (older Hibernate versions could duplicate joins for
     * fetched-vs-filtered mixes).</p>
     */
    static Join<Product, Category> categoryJoin(jakarta.persistence.criteria.Root<Product> root) {
        return root.join("category", JoinType.LEFT);
    }

    /**
     * Exact-match filter on the category FK.
     *
     * <p>{@code categoryId == null} → no predicate (the filter is optional).
     * A non-null id filters on the LEFT-joined category's id; rows without a
     * category cannot match and are excluded, as expected. A negative or
     * unknown id yields an empty result rather than an error — same contract
     * as the exact-match name filter.</p>
     */
    public static Specification<Product> categoryId(Integer categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) {
                return cb.conjunction();
            }
            Join<Product, Category> category = categoryJoin(root);
            return cb.equal(category.get("id"), categoryId);
        };
    }

    /**
     * Inclusive lower bound on price; {@code null} → no predicate.
     */
    public static Specification<Product> priceFrom(BigDecimal priceFrom) {
        return (root, query, cb) ->
                priceFrom == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("price"), priceFrom);
    }

    /**
     * Inclusive upper bound on price; {@code null} → no predicate.
     */
    public static Specification<Product> priceTo(BigDecimal priceTo) {
        return (root, query, cb) ->
                priceTo == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("price"), priceTo);
    }

    /**
     * Combines every optional filter in one AND chain. {@code Specification}
     * composition semantics: each spec contributes at most one predicate
     * (name, category, priceFrom, priceTo), so the combined predicate count
     * is bounded and each contributes at most one join (category) — no
     * fan-out, no duplicate rows, pagination unaffected.
     */
    public static Specification<Product> withFilters(
            String name, Integer categoryId, BigDecimal priceFrom, BigDecimal priceTo) {
        List<Specification<Product>> specs = new ArrayList<>(4);
        specs.add(nameContains(name));
        specs.add(categoryId(categoryId));
        specs.add(priceFrom(priceFrom));
        specs.add(priceTo(priceTo));
        return combine(specs);
    }

    private static Specification<Product> combine(List<Specification<Product>> specs) {
        // Seed with a vacuous truth predicate so an all-null filter list is
        // still a valid Specification (WHERE TRUE) rather than null.
        Specification<Product> result = (root, query, cb) -> cb.conjunction();
        for (Specification<Product> spec : specs) {
            result = result.and(spec);
        }
        return result;
    }
}
