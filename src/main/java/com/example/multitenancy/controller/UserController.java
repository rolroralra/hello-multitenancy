package com.example.multitenancy.controller;

import com.example.multitenancy.config.tenant.TenantContext;
import com.example.multitenancy.domain.tenant.entity.User;
import com.example.multitenancy.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for user management.
 * Requires X-Tenant-ID header for tenant-specific operations.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get all users using JPA.
     */
    @GetMapping
    public List<UserResponse> getAllUsers() {
        validateTenant();
        return userService.findAllUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Get all users using jOOQ.
     */
    @GetMapping("/jooq")
    public List<Map<String, Object>> getAllUsersJooq() {
        validateTenant();
        return userService.findAllUsersWithJooq().intoMaps();
    }

    /**
     * Get user by ID using JPA.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        validateTenant();
        return userService.findById(id)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create user using JPA.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        validateTenant();
        User user = userService.createUser(request.email(), request.name());
        return UserResponse.from(user);
    }

    /**
     * Create user using jOOQ.
     */
    @PostMapping("/jooq")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUserJooq(@Valid @RequestBody CreateUserRequest request) {
        validateTenant();
        userService.createUserWithJooq(request.email(), request.name());

        Record record = userService.findUserByEmailWithJooq(request.email());
        return record != null ? record.intoMap() : Map.of("status", "created");
    }

    /**
     * Get user count using jOOQ.
     */
    @GetMapping("/count")
    public Map<String, Object> getUserCount() {
        validateTenant();
        int count = userService.countUsersWithJooq();
        return Map.of(
                "tenant", TenantContext.getTenantId(),
                "count", count
        );
    }

    private void validateTenant() {
        if (TenantContext.isDefaultTenant()) {
            throw new IllegalStateException("X-Tenant-ID header is required");
        }
    }

    // ========== DTOs ==========

    public record CreateUserRequest(
            @NotBlank @Email
            String email,

            @NotBlank
            String name
    ) {}

    public record UserResponse(
            Long id,
            String email,
            String name
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getName()
            );
        }
    }
}
