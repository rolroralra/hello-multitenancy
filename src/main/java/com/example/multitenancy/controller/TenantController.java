package com.example.multitenancy.controller;

import com.example.multitenancy.domain.master.entity.Tenant;
import com.example.multitenancy.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for tenant management.
 * These endpoints operate on the master (public) schema.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public List<TenantResponse> getAllTenants() {
        return tenantService.findAllTenants().stream()
                .map(TenantResponse::from)
                .toList();
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        return tenantService.findByTenantId(tenantId)
                .map(TenantResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(request.tenantId(), request.name());
        return TenantResponse.from(tenant);
    }

    // ========== DTOs ==========

    public record CreateTenantRequest(
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "Tenant ID must start with a letter and contain only alphanumeric characters and underscores")
            String tenantId,

            @NotBlank
            String name
    ) {}

    public record TenantResponse(
            Long id,
            String tenantId,
            String name,
            Boolean isActive
    ) {
        public static TenantResponse from(Tenant tenant) {
            return new TenantResponse(
                    tenant.getId(),
                    tenant.getTenantId(),
                    tenant.getName(),
                    tenant.getIsActive()
            );
        }
    }
}
