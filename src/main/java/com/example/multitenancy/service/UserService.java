package com.example.multitenancy.service;

import com.example.multitenancy.config.jooq.JooqConfig.TenantAwareDSLContextProvider;
import com.example.multitenancy.config.tenant.TenantContext;
import com.example.multitenancy.domain.tenant.entity.User;
import com.example.multitenancy.domain.tenant.repository.UserRepository;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

/**
 * Service for managing users.
 * Demonstrates both JPA and jOOQ usage with multitenancy.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // Schema name used during code generation (for RenderMapping)
    private static final String SCHEMA = "public";

    private final UserRepository userRepository;
    private final TenantAwareDSLContextProvider dslProvider;

    public UserService(UserRepository userRepository, TenantAwareDSLContextProvider dslProvider) {
        this.userRepository = userRepository;
        this.dslProvider = dslProvider;
    }

    // ========== JPA Methods ==========

    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        log.debug("Finding all users for tenant: {}", TenantContext.getTenantId());
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User createUser(String email, String name) {
        log.debug("Creating user for tenant: {}", TenantContext.getTenantId());
        User user = new User(email, name);
        return userRepository.save(user);
    }

    // ========== jOOQ Methods ==========

    /**
     * Finds all users using jOOQ.
     * The DSLContext automatically maps to the current tenant's schema via RenderMapping.
     * Note: Schema must be specified for RenderMapping to work.
     */
    @Transactional(readOnly = true)
    public Result<Record> findAllUsersWithJooq() {
        log.debug("Finding all users with jOOQ for tenant: {}", TenantContext.getTenantId());

        return dslProvider.get()
                .select()
                .from(table(name(SCHEMA, "users")))
                .fetch();
    }

    /**
     * Finds a user by email using jOOQ.
     */
    @Transactional(readOnly = true)
    public Record findUserByEmailWithJooq(String email) {
        return dslProvider.get()
                .select()
                .from(table(name(SCHEMA, "users")))
                .where(field("email").eq(email))
                .fetchOne();
    }

    /**
     * Creates a user using jOOQ.
     */
    @Transactional
    public int createUserWithJooq(String email, String name) {
        log.debug("Creating user with jOOQ for tenant: {}", TenantContext.getTenantId());

        return dslProvider.get()
                .insertInto(table(name(SCHEMA, "users")))
                .columns(field("email"), field("name"), field("created_at"))
                .values(email, name, currentTimestamp())
                .execute();
    }

    /**
     * Counts users using jOOQ.
     */
    @Transactional(readOnly = true)
    public int countUsersWithJooq() {
        return dslProvider.get()
                .selectCount()
                .from(table(name(SCHEMA, "users")))
                .fetchOne(0, int.class);
    }
}
