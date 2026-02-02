package com.example.multitenancy.service;

import com.example.multitenancy.config.jooq.JooqConfig.TenantAwareDSLContextProvider;
import com.example.multitenancy.config.tenant.TenantContext;
import com.example.multitenancy.domain.tenant.entity.Product;
import com.example.multitenancy.domain.tenant.repository.ProductRepository;
import com.example.multitenancy.jooq.generated.tables.records.ProductsRecord;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.example.multitenancy.jooq.generated.Tables.PRODUCTS;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.sum;

/**
 * Service for managing products.
 * Demonstrates both JPA and jOOQ usage with multitenancy.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final TenantAwareDSLContextProvider dslProvider;

    public ProductService(ProductRepository productRepository, TenantAwareDSLContextProvider dslProvider) {
        this.productRepository = productRepository;
        this.dslProvider = dslProvider;
    }

    // ========== JPA Methods ==========

    @Transactional(readOnly = true)
    public List<Product> findAllProducts() {
        log.debug("Finding all products for tenant: {}", TenantContext.getTenantId());
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    public Product createProduct(String name, BigDecimal price, String description) {
        log.debug("Creating product for tenant: {}", TenantContext.getTenantId());
        Product product = new Product(name, price, description);
        return productRepository.save(product);
    }

    // ========== jOOQ Methods (Type-safe with generated code) ==========

    /**
     * Finds all products using jOOQ.
     * Uses generated table reference - RenderMapping transforms tenant_a -> current tenant.
     */
    @Transactional(readOnly = true)
    public Result<ProductsRecord> findAllProductsWithJooq() {
        log.debug("Finding all products with jOOQ for tenant: {}", TenantContext.getTenantId());

        return dslProvider.get()
                .selectFrom(PRODUCTS)
                .orderBy(PRODUCTS.CREATED_AT.desc())
                .fetch();
    }

    /**
     * Finds products by name pattern using jOOQ.
     */
    @Transactional(readOnly = true)
    public Result<ProductsRecord> findProductsByNameWithJooq(String namePattern) {
        return dslProvider.get()
                .selectFrom(PRODUCTS)
                .where(PRODUCTS.NAME.likeIgnoreCase("%" + namePattern + "%"))
                .fetch();
    }

    /**
     * Creates a product using jOOQ.
     */
    @Transactional
    public int createProductWithJooq(String name, BigDecimal price, String description) {
        log.debug("Creating product with jOOQ for tenant: {}", TenantContext.getTenantId());

        return dslProvider.get()
                .insertInto(PRODUCTS, PRODUCTS.NAME, PRODUCTS.PRICE, PRODUCTS.DESCRIPTION)
                .values(name, price, description)
                .execute();
    }

    /**
     * Calculates total value of all products using jOOQ.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalValueWithJooq() {
        return dslProvider.get()
                .select(sum(PRODUCTS.PRICE))
                .from(PRODUCTS)
                .fetchOne(0, BigDecimal.class);
    }

    /**
     * Gets product statistics using jOOQ.
     */
    @Transactional(readOnly = true)
    public Record getProductStatsWithJooq() {
        return dslProvider.get()
                .select(
                        count().as("total_count"),
                        sum(PRODUCTS.PRICE).as("total_value"),
                        avg(PRODUCTS.PRICE).as("avg_price"),
                        min(PRODUCTS.PRICE).as("min_price"),
                        max(PRODUCTS.PRICE).as("max_price")
                )
                .from(PRODUCTS)
                .fetchOne();
    }
}
