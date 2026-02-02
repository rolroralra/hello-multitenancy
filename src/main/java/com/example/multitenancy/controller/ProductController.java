package com.example.multitenancy.controller;

import com.example.multitenancy.config.tenant.TenantContext;
import com.example.multitenancy.domain.tenant.entity.Product;
import com.example.multitenancy.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for product management.
 * Requires X-Tenant-ID header for tenant-specific operations.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all products using JPA.
     */
    @GetMapping
    public List<ProductResponse> getAllProducts() {
        validateTenant();
        return productService.findAllProducts().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Get all products using jOOQ.
     */
    @GetMapping("/jooq")
    public List<Map<String, Object>> getAllProductsJooq() {
        validateTenant();
        return productService.findAllProductsWithJooq().intoMaps();
    }

    /**
     * Get product by ID using JPA.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        validateTenant();
        return productService.findById(id)
                .map(ProductResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search products by name using jOOQ.
     */
    @GetMapping("/search")
    public List<Map<String, Object>> searchProducts(@RequestParam String name) {
        validateTenant();
        return productService.findProductsByNameWithJooq(name).intoMaps();
    }

    /**
     * Create product using JPA.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        validateTenant();
        Product product = productService.createProduct(
                request.name(),
                request.price(),
                request.description()
        );
        return ProductResponse.from(product);
    }

    /**
     * Create product using jOOQ.
     */
    @PostMapping("/jooq")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProductJooq(@Valid @RequestBody CreateProductRequest request) {
        validateTenant();
        productService.createProductWithJooq(
                request.name(),
                request.price(),
                request.description()
        );
        return Map.of(
                "status", "created",
                "tenant", TenantContext.getTenantId()
        );
    }

    /**
     * Get product statistics using jOOQ.
     */
    @GetMapping("/stats")
    public Map<String, Object> getProductStats() {
        validateTenant();
        Record stats = productService.getProductStatsWithJooq();
        return Map.of(
                "tenant", TenantContext.getTenantId(),
                "totalCount", stats.get("total_count"),
                "totalValue", stats.get("total_value"),
                "avgPrice", stats.get("avg_price"),
                "minPrice", stats.get("min_price"),
                "maxPrice", stats.get("max_price")
        );
    }

    private void validateTenant() {
        if (TenantContext.isDefaultTenant()) {
            throw new IllegalStateException("X-Tenant-ID header is required");
        }
    }

    // ========== DTOs ==========

    public record CreateProductRequest(
            @NotBlank
            String name,

            @NotNull @Positive
            BigDecimal price,

            String description
    ) {}

    public record ProductResponse(
            Long id,
            String name,
            BigDecimal price,
            String description
    ) {
        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getDescription()
            );
        }
    }
}
