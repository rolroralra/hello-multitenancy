# Multitenancy Demo

Java 21 + Spring Boot 3 + JPA + jOOQ를 사용한 Schema-per-Tenant 멀티테넌시 실습 프로젝트

## 기술 스택

- Java 21
- Spring Boot 3.3.5
- Spring Data JPA (Hibernate 6)
- jOOQ 3.19
- PostgreSQL 16
- Spring Docker Compose Support
- Gradle (Kotlin DSL)

## 아키텍처

```
PostgreSQL Database (multitenancy_db)
├── public       # 마스터 데이터 (테넌트 정보)
├── tenant_a     # 테넌트 A 데이터
└── tenant_b     # 테넌트 B 데이터
```

### 멀티테넌시 구현 방식

| 구성 요소 | 방식 |
|----------|------|
| JPA/Hibernate | `MultiTenantConnectionProvider` + PostgreSQL `search_path` |
| jOOQ | `TenantAwareDSLContextProvider` + `RenderMapping` + Configuration 캐싱 |

## 프로젝트 구조

```
src/main/java/com/example/multitenancy/
├── config/
│   ├── tenant/     # TenantContext, ConnectionProvider, Resolver
│   ├── jpa/        # Hibernate 멀티테넌시 설정
│   ├── jooq/       # jOOQ Provider + Cache 멀티테넌시 설정
│   └── web/        # TenantInterceptor (X-Tenant-ID 헤더 처리)
├── domain/
│   ├── master/     # Tenant 엔티티 (public 스키마)
│   └── tenant/     # User, Product 엔티티 (테넌트별 스키마)
├── service/        # JPA & jOOQ 사용 서비스
└── controller/     # REST API
```

## 실행 방법

### 사전 요구사항

- Java 21
- Docker & Docker Compose

### 애플리케이션 실행

```bash
# Gradle Wrapper로 실행 (Docker Compose 자동 시작)
./gradlew bootRun
```

Spring Boot Docker Compose Support가 자동으로 PostgreSQL 컨테이너를 시작합니다.

### 수동 Docker 실행 (선택)

```bash
# Docker Compose로 PostgreSQL 시작
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

## API 문서

### 테넌트 관리 (Master)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/tenants` | 전체 테넌트 목록 |
| GET | `/api/tenants/{tenantId}` | 테넌트 상세 |
| POST | `/api/tenants` | 테넌트 생성 |

### 사용자 관리 (Tenant-specific)

> `X-Tenant-ID` 헤더 필수

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/users` | 사용자 목록 (JPA) |
| GET | `/api/users/jooq` | 사용자 목록 (jOOQ) |
| GET | `/api/users/{id}` | 사용자 상세 |
| GET | `/api/users/count` | 사용자 수 (jOOQ) |
| POST | `/api/users` | 사용자 생성 (JPA) |
| POST | `/api/users/jooq` | 사용자 생성 (jOOQ) |

### 상품 관리 (Tenant-specific)

> `X-Tenant-ID` 헤더 필수

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/products` | 상품 목록 (JPA) |
| GET | `/api/products/jooq` | 상품 목록 (jOOQ) |
| GET | `/api/products/{id}` | 상품 상세 |
| GET | `/api/products/search?name=` | 상품 검색 (jOOQ) |
| GET | `/api/products/stats` | 상품 통계 (jOOQ) |
| POST | `/api/products` | 상품 생성 (JPA) |
| POST | `/api/products/jooq` | 상품 생성 (jOOQ) |

## API 테스트

### IntelliJ HTTP Client

`http/multitenancy.http` 파일을 IntelliJ IDEA에서 열어 API를 테스트할 수 있습니다.

```
http/
├── multitenancy.http      # API 테스트 파일
└── http-client.env.json   # 환경 변수 (local, docker)
```

### curl 사용 예시

### 테넌트 목록 조회

```bash
curl http://localhost:8080/api/tenants
```

```json
[
  {"id": 1, "tenantId": "tenant_a", "name": "Tenant A Company", "isActive": true},
  {"id": 2, "tenantId": "tenant_b", "name": "Tenant B Company", "isActive": true}
]
```

### 테넌트별 사용자 조회

```bash
# tenant_a 사용자
curl http://localhost:8080/api/users -H "X-Tenant-ID: tenant_a"

# tenant_b 사용자 (다른 데이터)
curl http://localhost:8080/api/users -H "X-Tenant-ID: tenant_b"
```

### 사용자 생성

```bash
curl -X POST http://localhost:8080/api/users \
  -H "X-Tenant-ID: tenant_a" \
  -H "Content-Type: application/json" \
  -d '{"email": "new@tenant-a.com", "name": "New User"}'
```

### 상품 통계 조회 (jOOQ)

```bash
curl http://localhost:8080/api/products/stats -H "X-Tenant-ID: tenant_a"
```

```json
{
  "tenant": "tenant_a",
  "totalCount": 2,
  "totalValue": 300.00,
  "avgPrice": 150.00,
  "minPrice": 100.00,
  "maxPrice": 200.00
}
```

### 새 테넌트 생성

```bash
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "tenant_c", "name": "Tenant C Company"}'
```

## jOOQ 코드 생성 (선택)

이 프로젝트는 동적 DSL(`DSL.table(name("..."))`)을 사용하므로 코드 생성 없이도 동작합니다.

타입 안전한 jOOQ 클래스가 필요한 경우:

```bash
# PostgreSQL 실행 중일 때
./gradlew generateJooq
```

## 문서

- [JPA Multitenancy 설정](docs/jpa-multitenancy.md)
- [jOOQ Multitenancy 설정](docs/jooq-multitenancy.md)
- [jOOQ DSLContext 전략 비교](docs/jooq-dslcontext-strategies.md) - Request Scope vs Provider + Cache

## 핵심 코드

### TenantContext

```java
public final class TenantContext {
    private static final InheritableThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    public static void setTenantId(String tenantId) { CURRENT_TENANT.set(tenantId); }
    public static String getTenantId() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
```

### jOOQ 사용법

**중요**: RenderMapping이 작동하려면 스키마를 명시해야 합니다.

```java
private static final String SCHEMA = "public";  // RenderMapping 입력 스키마

// 스키마 명시 필수!
dslProvider.get()
    .select()
    .from(table(name(SCHEMA, "users")))  // "public" -> "tenant_a" 로 매핑됨
    .fetch();
```

**왜 Provider + Cache 방식인가?**
- Configuration 캐싱으로 성능 최적화
- 웹 요청 외부(배치, 스케줄러)에서도 사용 가능
- `getForTenant()`로 cross-tenant 접근 가능
- 프록시 오버헤드 없음

### Hibernate ConnectionProvider

```java
@Override
public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = dataSource.getConnection();
    connection.createStatement()
        .execute("SET search_path TO " + tenantIdentifier + ", public");
    return connection;
}
```
