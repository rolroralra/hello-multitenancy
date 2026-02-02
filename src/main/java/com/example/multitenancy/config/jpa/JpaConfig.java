package com.example.multitenancy.config.jpa;

import com.example.multitenancy.config.tenant.SchemaMultiTenantConnectionProvider;
import com.example.multitenancy.config.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * JPA/Hibernate configuration for schema-based multitenancy.
 */
@Configuration
public class JpaConfig {

    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final SchemaMultiTenantConnectionProvider connectionProvider;

    public JpaConfig(TenantIdentifierResolver tenantIdentifierResolver,
                     SchemaMultiTenantConnectionProvider connectionProvider) {
        this.tenantIdentifierResolver = tenantIdentifierResolver;
        this.connectionProvider = connectionProvider;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
