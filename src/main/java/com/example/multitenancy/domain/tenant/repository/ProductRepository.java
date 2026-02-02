package com.example.multitenancy.domain.tenant.repository;

import com.example.multitenancy.domain.tenant.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for tenant-specific product data.
 * Automatically operates on the current tenant's schema via Hibernate multitenancy.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String name);
}
