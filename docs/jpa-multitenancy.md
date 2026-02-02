# JPA Multitenancy 설정

## 개요

이 프로젝트는 Hibernate 6의 Schema-based Multitenancy를 사용합니다. 각 테넌트는 PostgreSQL의 별도 스키마에 데이터를 저장합니다.

## 핵심 구성 요소

### 1. TenantContext

`InheritableThreadLocal`을 사용하여 현재 요청의 테넌트 ID를 저장합니다.

```java
public final class TenantContext {
    public static final String DEFAULT_TENANT = "public";
    private static final InheritableThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    public static void setTenantId(String tenantId) { ... }
    public static String getTenantId() { ... }
    public static void clear() { ... }
}
```

### 2. TenantIdentifierResolver

Hibernate가 현재 테넌트를 식별할 때 사용하는 리졸버입니다.

```java
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {
    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getTenantId();
    }
}
```

### 3. SchemaMultiTenantConnectionProvider

테넌트별로 PostgreSQL의 `search_path`를 설정하여 스키마를 전환합니다.

```java
@Override
public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = getAnyConnection();
    connection.createStatement()
        .execute("SET search_path TO " + tenantIdentifier + ", public");
    return connection;
}
```

### 4. JpaConfig

Hibernate 속성에 멀티테넌시 설정을 등록합니다.

```java
@Bean
public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
    return (hibernateProperties) -> {
        hibernateProperties.put(MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
    };
}
```

## 동작 방식

1. HTTP 요청이 들어오면 `TenantInterceptor`가 `X-Tenant-ID` 헤더를 읽어 `TenantContext`에 설정
2. JPA Repository 메소드 호출 시 `TenantIdentifierResolver`가 현재 테넌트 반환
3. `SchemaMultiTenantConnectionProvider`가 해당 테넌트의 스키마로 `search_path` 설정
4. 쿼리 실행 후 연결 반환 시 `search_path`를 `public`으로 초기화

## 주의사항

- 테넌트 ID는 SQL Injection 방지를 위해 sanitize 처리됨
- `@Async` 메소드에서도 테넌트 컨텍스트가 전파됨 (`InheritableThreadLocal` 사용)
- Master 데이터(테넌트 정보)는 항상 `public` 스키마의 엔티티 사용 (`@Table(schema = "public")`)
