package com.ichaabane.ecommerce.controller;

import com.ichaabane.ecommerce.dto.request.ProductPurchaseRequest;
import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.PageResponse;
import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService service;

    @PostMapping
    public ResponseEntity<Integer> createProduct(
            @RequestBody @Valid ProductRequest request
    ) {
        Integer id = service.createProduct(request);
        // 201 + Location for a successful creation, instead of the previous 200.
        return ResponseEntity.created(URI.create("/api/v1/products/" + id)).body(id);
    }

    @PostMapping("/purchase")
    public ResponseEntity<List<ProductPurchaseResponse>> purchaseProducts(
            @RequestBody @Valid List<@Valid ProductPurchaseRequest> request
    ) {
        return ResponseEntity.ok(service.purchaseProducts(request));
    }

    @GetMapping("/{product-id}")
    public ResponseEntity<ProductResponse> findById(
            @PathVariable("product-id") Integer productId
    ) {
        return ResponseEntity.ok(service.findById(productId));
    }

    /**
     * Paginated, sortable catalogue endpoint.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Page size must be >= 1") @Max(value = 100, message = "Page size must be <= 100") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order
    ) {
        return ResponseEntity.ok(service.findAll(page, size, sort, order));
    }

    /**
     * Paginated, filtered catalogue search. 
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<ProductResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal priceFrom,
            @RequestParam(required = false) BigDecimal priceTo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Page size must be >= 1") @Max(value = 100, message = "Page size must be <= 100") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order
    ) {
        return ResponseEntity.ok(
                service.search(name, categoryId, priceFrom, priceTo, page, size, sort, order));
    }
}
