-- Master table in public schema
CREATE TABLE IF NOT EXISTS public.tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- Function to create tenant schema with tables
CREATE OR REPLACE FUNCTION create_tenant_schema(schema_name TEXT)
RETURNS VOID AS $$
BEGIN
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', schema_name);

    -- Users table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.users (
            id BIGSERIAL PRIMARY KEY,
            email VARCHAR(255) UNIQUE NOT NULL,
            name VARCHAR(255) NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )', schema_name);

    -- Products table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.products (
            id BIGSERIAL PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            price DECIMAL(10,2) NOT NULL,
            description TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )', schema_name);
END;
$$ LANGUAGE plpgsql;

-- Create sample tenant schemas
SELECT create_tenant_schema('tenant_a');
SELECT create_tenant_schema('tenant_b');

-- Insert sample tenant data
INSERT INTO public.tenants (tenant_id, name) VALUES
    ('tenant_a', 'Tenant A Company'),
    ('tenant_b', 'Tenant B Company')
ON CONFLICT (tenant_id) DO NOTHING;

-- Insert sample data for tenant_a
INSERT INTO tenant_a.users (email, name) VALUES
    ('admin@tenant-a.com', 'Admin A'),
    ('user@tenant-a.com', 'User A')
ON CONFLICT DO NOTHING;

INSERT INTO tenant_a.products (name, price, description) VALUES
    ('Product A1', 100.00, 'First product of Tenant A'),
    ('Product A2', 200.00, 'Second product of Tenant A')
ON CONFLICT DO NOTHING;

-- Insert sample data for tenant_b
INSERT INTO tenant_b.users (email, name) VALUES
    ('admin@tenant-b.com', 'Admin B'),
    ('user@tenant-b.com', 'User B')
ON CONFLICT DO NOTHING;

INSERT INTO tenant_b.products (name, price, description) VALUES
    ('Product B1', 150.00, 'First product of Tenant B'),
    ('Product B2', 250.00, 'Second product of Tenant B')
ON CONFLICT DO NOTHING;
