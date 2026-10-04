package com.ichaabane.ecommerce.service;

import com.ichaabane.ecommerce.dto.request.ProductPurchaseRequest;
import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import com.ichaabane.ecommerce.mapper.ProductMapper;
import com.ichaabane.ecommerce.repository.CategoryRepository;
import com.ichaabane.ecommerce.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
                .orElseThrow(() -> new EntityNotFoundException("Product not found with ID:: " + id));
    }

    public List<ProductResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = ProductPurchaseException.class)
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
