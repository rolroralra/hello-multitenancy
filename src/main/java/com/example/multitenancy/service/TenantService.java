package com.example.multitenancy.service;

import com.example.multitenancy.domain.master.entity.Tenant;
import com.example.multitenancy.domain.master.repository.TenantRepository;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing tenants.
 * Uses the master DSLContext to always operate on the public schema.
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final DSLContext masterDsl;

    public TenantService(TenantRepository tenantRepository,
                         @Qualifier("masterDslContext") DSLContext masterDsl) {
        this.tenantRepository = tenantRepository;
        this.masterDsl = masterDsl;
    }

    @Transactional(readOnly = true)
    public List<Tenant> findAllTenants() {
        return tenantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> findByTenantId(String tenantId) {
        return tenantRepository.findByTenantId(tenantId);
    }

    @Transactional
    public Tenant createTenant(String tenantId, String name) {
        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("Tenant already exists: " + tenantId);
        }

        // Create the tenant schema
        createTenantSchema(tenantId);

        // Save tenant metadata
        Tenant tenant = new Tenant(tenantId, name);
        return tenantRepository.save(tenant);
    }

    /**
     * Creates a new schema for the tenant with required tables.
     * Uses raw SQL via jOOQ's master DSLContext.
     */
    private void createTenantSchema(String tenantId) {
        String sanitizedTenantId = sanitizeTenantId(tenantId);

        log.info("Creating schema for tenant: {}", sanitizedTenantId);

        // Create schema
        masterDsl.execute("CREATE SCHEMA IF NOT EXISTS " + sanitizedTenantId);

        // Create users table
        masterDsl.execute("""
            CREATE TABLE IF NOT EXISTS %s.users (
                id BIGSERIAL PRIMARY KEY,
                email VARCHAR(255) UNIQUE NOT NULL,
                name VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.formatted(sanitizedTenantId));

        // Create products table
        masterDsl.execute("""
            CREATE TABLE IF NOT EXISTS %s.products (
                id BIGSERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                price DECIMAL(10,2) NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.formatted(sanitizedTenantId));

        log.info("Schema created successfully for tenant: {}", sanitizedTenantId);
    }

    private String sanitizeTenantId(String tenantId) {
        if (tenantId == null || !tenantId.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid tenant ID: " + tenantId);
        }
        return tenantId;
    }
}
