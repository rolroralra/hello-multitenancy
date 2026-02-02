package com.example.multitenancy.service;

import com.example.multitenancy.config.jooq.JooqConfig.TenantAwareDSLContextProvider;
import com.example.multitenancy.config.tenant.TenantContext;
import com.example.multitenancy.domain.tenant.entity.User;
import com.example.multitenancy.domain.tenant.repository.UserRepository;
import com.example.multitenancy.jooq.generated.tables.records.UsersRecord;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.multitenancy.jooq.generated.Tables.USERS;

/**
 * Service for managing users.
 * Demonstrates both JPA and jOOQ usage with multitenancy.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

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

    // ========== jOOQ Methods (Type-safe with generated code) ==========

    /**
     * Finds all users using jOOQ.
     * Uses generated table reference - RenderMapping transforms tenant_a -> current tenant.
     */
    @Transactional(readOnly = true)
    public Result<UsersRecord> findAllUsersWithJooq() {
        log.debug("Finding all users with jOOQ for tenant: {}", TenantContext.getTenantId());

        return dslProvider.get()
                .selectFrom(USERS)
                .fetch();
    }

    /**
     * Finds a user by email using jOOQ.
     */
    @Transactional(readOnly = true)
    public UsersRecord findUserByEmailWithJooq(String email) {
        return dslProvider.get()
                .selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOne();
    }

    /**
     * Creates a user using jOOQ.
     */
    @Transactional
    public int createUserWithJooq(String email, String name) {
        log.debug("Creating user with jOOQ for tenant: {}", TenantContext.getTenantId());

        return dslProvider.get()
                .insertInto(USERS, USERS.EMAIL, USERS.NAME)
                .values(email, name)
                .execute();
    }

    /**
     * Counts users using jOOQ.
     */
    @Transactional(readOnly = true)
    public int countUsersWithJooq() {
        return dslProvider.get()
                .selectCount()
                .from(USERS)
                .fetchOne(0, int.class);
    }
}
