package com.example.multitenancy.domain.master.repository;

import com.example.multitenancy.domain.master.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for master tenant data.
 * Always operates on the public schema.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);
}
