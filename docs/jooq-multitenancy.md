# jOOQ Multitenancy 설정

## jOOQ 개요

jOOQ(Java Object Oriented Querying)는 타입 안전한 SQL 쿼리를 Java로 작성할 수 있게 해주는 라이브러리입니다.

### 주요 특징

- **타입 안전성**: 컴파일 타임에 SQL 오류 검출
- **코드 생성**: 데이터베이스 스키마로부터 Java 클래스 자동 생성
- **SQL 친화적**: 복잡한 SQL도 직관적으로 작성 가능
- **ORM 대안**: JPA와 함께 또는 독립적으로 사용 가능

### 기본 사용법

```java
// SELECT
dsl.select(USERS.ID, USERS.NAME)
   .from(USERS)
   .where(USERS.EMAIL.eq("user@example.com"))
   .fetch();

// INSERT
dsl.insertInto(USERS)
   .columns(USERS.EMAIL, USERS.NAME)
   .values("user@example.com", "User Name")
   .execute();

// UPDATE
dsl.update(USERS)
   .set(USERS.NAME, "New Name")
   .where(USERS.ID.eq(1L))
   .execute();
```

## Multitenancy 구현

이 프로젝트에서는 **Provider + Cache** 방식을 사용합니다.

### 왜 이 방식인가?

| 방식 | 장점 | 단점 |
|------|------|------|
| **Provider + Cache (선택)** | Configuration 캐싱, 높은 성능, 넓은 사용 범위 | 약간 더 많은 코드 |
| Request Scope | 단순한 주입 | 매 요청 객체 생성, 웹 외부 사용 불가 |
| ConnectionProvider | Hibernate와 통합 용이 | 매 쿼리마다 SET 명령 실행 |
| ExecuteListener | 유연한 커스터마이징 | 성능 오버헤드 |

> 상세 비교는 [jOOQ DSLContext 전략 비교](jooq-dslcontext-strategies.md) 문서를 참조하세요.

### 핵심 설정

#### JooqConfig.java

```java
@Configuration
public class JooqConfig {

    private static final String CODE_GEN_SCHEMA = "public";

    @Bean
    @Qualifier("baseConfiguration")
    public org.jooq.Configuration baseConfiguration(DataSource dataSource) {
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setSQLDialect(SQLDialect.POSTGRES);
        configuration.setDataSource(dataSource);
        return configuration;
    }

    @Bean
    public TenantAwareDSLContextProvider dslContextProvider(
            @Qualifier("baseConfiguration") org.jooq.Configuration baseConfiguration) {
        return new TenantAwareDSLContextProvider(baseConfiguration);
    }

    /**
     * Provider for tenant-aware DSLContext.
     * Caches Configuration objects per tenant for optimal performance.
     */
    public static class TenantAwareDSLContextProvider {
        private final org.jooq.Configuration baseConfiguration;
        private final ConcurrentMap<String, org.jooq.Configuration> configurationCache
            = new ConcurrentHashMap<>();

        public DSLContext get() {
            String tenantId = TenantContext.getTenantId();
            org.jooq.Configuration config = configurationCache.computeIfAbsent(
                tenantId, this::createTenantConfiguration);
            return DSL.using(config);
        }

        public DSLContext getForTenant(String tenantId) {
            org.jooq.Configuration config = configurationCache.computeIfAbsent(
                tenantId, this::createTenantConfiguration);
            return DSL.using(config);
        }

        private org.jooq.Configuration createTenantConfiguration(String tenantId) {
            Settings tenantSettings = new Settings()
                .withRenderMapping(new RenderMapping()
                    .withSchemata(new MappedSchema()
                        .withInput(CODE_GEN_SCHEMA)
                        .withOutput(tenantId)));
            return baseConfiguration.derive(tenantSettings);
        }

        public void clearCache() {
            configurationCache.clear();
        }
    }
}
```

### 동작 방식

1. **코드 생성**: `public` 스키마 기준으로 jOOQ 클래스 생성
2. **Configuration 캐싱**: 테넌트별 Configuration을 ConcurrentHashMap에 캐싱
3. **RenderMapping**: SQL 렌더링 시 `public` -> `tenant_a` 등으로 스키마 변환
4. **derive()**: 기본 Configuration에서 테넌트별 설정만 파생 (불변, 스레드 안전)

### SQL 변환 예시

**중요**: RenderMapping이 작동하려면 테이블 참조 시 스키마를 명시해야 합니다.

```java
// 스키마 명시 (RenderMapping 작동)
dslProvider.get().select().from(table(name("public", "users"))).fetch();

// 스키마 없음 (RenderMapping 작동 안 함 - 오류 발생)
dslProvider.get().select().from(table(name("users"))).fetch();
```

생성 SQL (tenant_a):
```sql
SELECT * FROM "tenant_a"."users"
```

생성 SQL (tenant_b):
```sql
SELECT * FROM "tenant_b"."users"
```

### Master 데이터 접근

테넌트 관리 등 `public` 스키마에 접근할 때는 별도의 `masterDslContext` 사용:

```java
@Bean
@Qualifier("masterDslContext")
public DSLContext masterDslContext(@Qualifier("baseConfiguration") Configuration baseConfig) {
    return DSL.using(baseConfig);  // RenderMapping 없이 사용
}
```

## 사용 예시

### JPA와 jOOQ 함께 사용

```java
@Service
public class UserService {
    private static final String SCHEMA = "public";  // RenderMapping 입력 스키마

    private final UserRepository userRepository;  // JPA
    private final TenantAwareDSLContextProvider dslProvider;  // jOOQ

    // JPA 사용 (간단한 CRUD)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    // jOOQ 사용 - 스키마 명시 필수!
    public Result<Record> findAllUsersWithJooq() {
        return dslProvider.get()
                .select()
                .from(table(name(SCHEMA, "users")))  // "public" -> "tenant_a" 로 매핑
                .fetch();
    }

    // 특정 테넌트 데이터 조회 (cross-tenant)
    public Result<Record> findUsersForTenant(String tenantId) {
        return dslProvider.getForTenant(tenantId)
                .select()
                .from(table(name(SCHEMA, "users")))
                .fetch();
    }
}
```

### 언제 어떤 것을 사용할까?

- **JPA**: 단순 CRUD, 엔티티 관계 매핑, 캐싱이 필요한 경우
- **jOOQ**: 복잡한 조인, 집계, 동적 쿼리, 성능이 중요한 경우

## 코드 생성

### Gradle 설정 (build.gradle.kts)

```kotlin
jooq {
    version.set("3.19.13")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false) // 자동 생성 비활성화
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/multitenancy_db"
                    user = "app_user"
                    password = "app_password"
                }
                generator.apply {
                    database.apply {
                        inputSchema = "public"  // 코드 생성은 public 기준
                    }
                    target.apply {
                        packageName = "com.example.multitenancy.jooq.generated"
                    }
                }
            }
        }
    }
}
```

### 코드 생성 실행

```bash
# PostgreSQL 실행 중일 때
./gradlew generateJooq
```

## 주의사항

1. **스키마 명시 필수**: `table(name("public", "users"))` 형태로 스키마를 반드시 명시해야 RenderMapping이 작동함
2. **스키마 일관성**: 모든 테넌트 스키마의 테이블 구조가 동일해야 함
3. **코드 생성 시점**: DB가 실행 중이어야 코드 생성 가능
4. **트랜잭션**: JPA와 jOOQ가 동일한 트랜잭션 컨텍스트 공유
5. **캐시 관리**: 테넌트 스키마 변경 시 `clearCache()` 또는 `evictTenant()` 호출

## 관련 문서

- [jOOQ DSLContext 전략 비교](jooq-dslcontext-strategies.md) - Request Scope vs Provider + Cache 상세 비교
