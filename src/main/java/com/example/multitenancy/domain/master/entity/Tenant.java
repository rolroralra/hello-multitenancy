package com.example.multitenancy.domain.master.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Master tenant entity stored in the public schema.
 * Contains metadata about each tenant.
 */
@Entity
@Table(name = "tenants", schema = "public")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", unique = true, nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public Tenant() {
    }

    public Tenant(String tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
