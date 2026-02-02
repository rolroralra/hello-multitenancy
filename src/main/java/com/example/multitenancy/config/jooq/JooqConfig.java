package com.example.multitenancy.config.jooq;

import com.example.multitenancy.config.tenant.TenantContext;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * jOOQ configuration for schema-based multitenancy.
 *
 * Uses tenant-specific Configuration caching for optimal performance.
 * Configuration objects are immutable and thread-safe, so they can be safely cached.
 */
@Configuration
public class JooqConfig {

    private static final String CODE_GEN_SCHEMA = "public";

    private final DataSource dataSource;

    public JooqConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Base jOOQ configuration without tenant-specific settings.
     */
    @Bean
    @Qualifier("baseConfiguration")
    public org.jooq.Configuration baseConfiguration() {
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setSQLDialect(SQLDialect.POSTGRES);
        configuration.setDataSource(dataSource);
        return configuration;
    }

    /**
     * Tenant-aware DSLContext provider.
     *
     * Caches Configuration per tenant for performance.
     * Configuration is immutable and thread-safe, so caching is safe.
     * DSLContext itself is lightweight - it's just a wrapper around Configuration.
     */
    @Bean
    public TenantAwareDSLContextProvider dslContextProvider(
            @Qualifier("baseConfiguration") org.jooq.Configuration baseConfiguration) {
        return new TenantAwareDSLContextProvider(baseConfiguration);
    }

    /**
     * Primary DSLContext bean that delegates to current tenant's context.
     *
     * This is NOT request-scoped. Instead, it returns a DSLContext
     * based on the current TenantContext at the time of use.
     *
     * For most use cases, inject TenantAwareDSLContextProvider and call get().
     * This bean exists for compatibility with code expecting DSLContext injection.
     */
    @Bean
    @Qualifier("masterDslContext")
    public DSLContext masterDslContext(
            @Qualifier("baseConfiguration") org.jooq.Configuration baseConfiguration) {
        return DSL.using(baseConfiguration);
    }

    /**
     * Provider for tenant-aware DSLContext.
     * Caches Configuration objects per tenant for optimal performance.
     */
    public static class TenantAwareDSLContextProvider {

        private final org.jooq.Configuration baseConfiguration;
        private final ConcurrentMap<String, org.jooq.Configuration> configurationCache;

        public TenantAwareDSLContextProvider(org.jooq.Configuration baseConfiguration) {
            this.baseConfiguration = baseConfiguration;
            this.configurationCache = new ConcurrentHashMap<>();
        }

        /**
         * Gets DSLContext for the current tenant.
         * Configuration is cached per tenant; DSLContext is created on each call
         * but this is cheap since it's just a thin wrapper.
         */
        public DSLContext get() {
            String tenantId = TenantContext.getTenantId();
            org.jooq.Configuration config = getConfiguration(tenantId);
            return DSL.using(config);
        }

        /**
         * Gets DSLContext for a specific tenant (useful for cross-tenant operations).
         */
        public DSLContext getForTenant(String tenantId) {
            org.jooq.Configuration config = getConfiguration(tenantId);
            return DSL.using(config);
        }

        /**
         * Gets or creates Configuration for the specified tenant.
         * Configurations are cached since they are immutable.
         */
        private org.jooq.Configuration getConfiguration(String tenantId) {
            if (TenantContext.DEFAULT_TENANT.equals(tenantId)) {
                return baseConfiguration;
            }

            return configurationCache.computeIfAbsent(tenantId, this::createTenantConfiguration);
        }

        private org.jooq.Configuration createTenantConfiguration(String tenantId) {
            Settings tenantSettings = new Settings()
                    .withRenderMapping(new RenderMapping()
                            .withSchemata(new MappedSchema()
                                    .withInput(CODE_GEN_SCHEMA)
                                    .withOutput(tenantId)));

            return baseConfiguration.derive(tenantSettings);
        }

        /**
         * Clears the configuration cache. Useful when tenant schemas change.
         */
        public void clearCache() {
            configurationCache.clear();
        }

        /**
         * Removes a specific tenant from the cache.
         */
        public void evictTenant(String tenantId) {
            configurationCache.remove(tenantId);
        }
    }
}
