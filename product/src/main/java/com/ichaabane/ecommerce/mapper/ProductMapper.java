package com.ichaabane.ecommerce.mapper;

import com.ichaabane.ecommerce.model.Category;
import com.ichaabane.ecommerce.model.Product;
import com.ichaabane.ecommerce.dto.response.ProductPurchaseResponse;
import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.dto.response.ProductResponse;
import org.springframework.stereotype.Service;

@Service
public class ProductMapper {
    public Product toProduct(ProductRequest request, Category category) {
        return Product.builder()
                .name(request.name())
                .description(request.description())
                .availableQuantity(request.availableQuantity())
                .price(request.price())
                .category(category)
                .build();
    }

    public ProductResponse toProductResponse(Product product) {
        var category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getDescription() : null
        );
    }

    public ProductPurchaseResponse toProductPurchaseResponse(Product product, double quantity) {
        return new ProductPurchaseResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                quantity
        );
    }
}
