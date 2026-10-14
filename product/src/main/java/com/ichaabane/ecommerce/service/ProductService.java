package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.dto.request.ProductPurchaseRequest;
import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.PageResponse;
import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import com.ichaabane.ecommerce.mapper.ProductMapper;
import com.ichaabane.ecommerce.repository.CategoryRepository;
import com.ichaabane.ecommerce.repository.ProductRepository;
import com.ichaabane.ecommerce.repository.ProductSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    /** Sort fields a client may request — anything else is rejected (whitelist). */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "price", "availableQuantity");

    /** Hard upper bound for a single page, regardless of what the client asks for. */
    private static final int MAX_PAGE_SIZE = 100;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ProductRepository repository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    public Integer createProduct(
            ProductRequest request
    ) {
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID:: " + request.categoryId()));
        var product = mapper.toProduct(request, category);
        return repository.save(product).getId();
    }

    public ProductResponse findById(Integer id) {
        return repository.findById(id)
                .map(mapper::toProductResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    /**
     * Paginated catalogue listing. Kept as {@code findAll(Pageable)} rather than
     * the unbounded {@code findAll()} the previous list endpoint used, since a
     * product catalogue grows without bound in production.
     *
     * <p>Sort fields are validated against {@link #ALLOWED_SORT_FIELDS} (the
     * "id" fallback keeps the ordering deterministic when the client sends a
     * sort parameter that is not whitelisted), page indexes must be
     * non-negative and page sizes are capped at {@link #MAX_PAGE_SIZE}.</p>
     */
    public PageResponse<ProductResponse> findAll(int page, int size, String sortField, String sortDirection) {
        Pageable pageable = buildPageable(page, size, sortField, sortDirection);
        Page<Product> productPage = repository.findAll(pageable);
        return PageResponse.from(productPage, mapper::toProductResponse);
    }

    /**
     * Paginated, filtered listing. All filters are optional and combine with
     * AND semantics; blank name searches, null category ids and open price
     * bounds are all ignored.
     */
    public PageResponse<ProductResponse> search(
            String name, Integer categoryId, BigDecimal priceFrom, BigDecimal priceTo,
            int page, int size, String sortField, String sortDirection) {
        Pageable pageable = buildPageable(page, size, sortField, sortDirection);
        Page<Product> productPage = repository.findAll(
                ProductSpecs.withFilters(name, categoryId, priceFrom, priceTo), pageable);
        return PageResponse.from(productPage, mapper::toProductResponse);
    }

    private Pageable buildPageable(int page, int size, String sortField, String sortDirection) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must be >= 0");
        }
        int effectiveSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sort = buildSort(sortField, sortDirection);
        return PageRequest.of(page, effectiveSize, sort);
    }

    private Sort buildSort(String sortField, String sortDirection) {
        String field = (sortField == null || sortField.isBlank()) ? "id" : sortField.trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unsupported sort field: " + field
                    + ". Allowed fields: " + ALLOWED_SORT_FIELDS);
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        // "name" ties are broken by id so the ordering is stable across pages.
        Sort primary = Sort.by(direction, field);
        return "id".equals(field) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
    public List<ProductPurchaseResponse> purchaseProducts(
            List<ProductPurchaseRequest> request
    ) {
        if (request == null || request.isEmpty()) {
            throw new ProductPurchaseException("At least one product must be purchased");
        }

        // Validate here as well: this method must be safe whatever the caller validated.
        // A zero/negative quantity would otherwise ADD stock. "!(q > 0)" also rejects NaN.
        // Duplicate product ids are merged (quantities summed), one entry per product.
        Map<Integer, Double> requestedQuantities = new TreeMap<>();
        for (var item : request) {
            if (item == null || item.productId() == null) {
                throw new ProductPurchaseException("Product is mandatory");
            }
            if (!(item.quantity() > 0)) {
                throw new ProductPurchaseException("Quantity must be positive for product with ID:: " + item.productId());
            }
            requestedQuantities.merge(item.productId(), item.quantity(), Double::sum);
        }

        var storedProducts = repository.findAllByIdInOrderById(new ArrayList<>(requestedQuantities.keySet()));
        if (storedProducts.size() != requestedQuantities.size()) {
            throw new ProductPurchaseException("One or more products does not exist");
        }

        var purchasedProducts = new ArrayList<ProductPurchaseResponse>();
        for (var product : storedProducts) {
            double quantity = requestedQuantities.get(product.getId());
            if (product.getAvailableQuantity() < quantity) {
                throw new ProductPurchaseException("Insufficient stock quantity for product with ID:: " + product.getId());
            }
            product.setAvailableQuantity(product.getAvailableQuantity() - quantity);
            repository.save(product);
            purchasedProducts.add(mapper.toProductPurchaseResponse(product, quantity));
        }
        return purchasedProducts;
    }
}
