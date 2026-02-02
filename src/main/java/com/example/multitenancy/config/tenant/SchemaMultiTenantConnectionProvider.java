package com.example.multitenancy.config.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Hibernate multi-tenant connection provider for schema-based multitenancy.
 * Switches PostgreSQL search_path based on tenant identifier.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Pattern VALID_TENANT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        try (Statement stmt = connection.createStatement()) {
            String schema = sanitizeTenantId(tenantIdentifier);
            stmt.execute("SET search_path TO " + schema + ", public");
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO public");
        }
        releaseAnyConnection(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }

    /**
     * Sanitizes tenant ID to prevent SQL injection.
     */
    private String sanitizeTenantId(String tenantId) {
        if (tenantId == null || !VALID_TENANT_PATTERN.matcher(tenantId).matches()) {
            return TenantContext.DEFAULT_TENANT;
        }
        return tenantId;
    }
}
