package com.example.multitenancy.config.tenant;

/**
 * ThreadLocal-based tenant context holder.
 * Uses InheritableThreadLocal to propagate tenant ID to child threads (e.g., @Async methods).
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT = "public";

    private static final InheritableThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    public static String getTenantIdOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static boolean isDefaultTenant() {
        return DEFAULT_TENANT.equals(getTenantId());
    }
}
