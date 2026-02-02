package com.example.multitenancy.domain.tenant.repository;

import com.example.multitenancy.domain.tenant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for tenant-specific user data.
 * Automatically operates on the current tenant's schema via Hibernate multitenancy.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
