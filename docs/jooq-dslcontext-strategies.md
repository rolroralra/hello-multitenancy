# jOOQ DSLContext 멀티테넌시 전략 비교

jOOQ에서 멀티테넌시를 구현할 때 DSLContext를 어떻게 제공할지에 대한 두 가지 주요 전략을 비교합니다.

## 1. Request Scope 방식

### 개념

Spring의 Request Scope를 사용하여 HTTP 요청마다 새로운 DSLContext를 생성합니다.

### 구현

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
    @Primary
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
    public DSLContext dslContext(@Qualifier("baseConfiguration") org.jooq.Configuration baseConfiguration) {
        String tenantId = TenantContext.getTenantId();

        if (TenantContext.isDefaultTenant()) {
            return DSL.using(baseConfiguration);
        }

        Settings tenantSettings = new Settings()
                .withRenderMapping(new RenderMapping()
                        .withSchemata(new MappedSchema()
                                .withInput(CODE_GEN_SCHEMA)
                                .withOutput(tenantId)));

        return DSL.using(baseConfiguration.derive(tenantSettings));
    }
}
```

### 사용법

```java
@Service
public class UserService {

    private final DSLContext dsl;  // 프록시 주입

    public UserService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Result<Record> findAllUsers() {
        // 프록시를 통해 현재 요청의 DSLContext에 접근
        // 주의: 스키마 명시 필수 (RenderMapping이 작동하려면)
        return dsl.select()
                .from(table(name("public", "users")))
                .fetch();
    }
}
```

### 장점

1. **단순한 주입**: `DSLContext`를 직접 주입받아 사용
2. **익숙한 패턴**: Spring의 일반적인 Scope 패턴과 동일
3. **자동 생명주기 관리**: 요청 종료 시 자동으로 정리

### 단점

1. **성능 오버헤드**
   - 매 요청마다 `Settings`, `Configuration`, `DSLContext` 객체 생성
   - 프록시를 통한 메서드 호출 비용

2. **사용 범위 제한**
   - 웹 요청 컨텍스트 외부에서 사용 불가
   - `@Scheduled`, `@Async`, 배치 작업에서 사용 어려움
   - 테스트 작성 시 웹 컨텍스트 필요

3. **Cross-tenant 접근 불가**
   - 현재 요청의 테넌트만 접근 가능
   - 다른 테넌트 데이터 조회 시 별도 빈 필요

4. **디버깅 어려움**
   - 프록시 레이어로 인해 스택 트레이스가 복잡해짐

---

## 2. Provider + Cache 방식

### 개념

테넌트별 Configuration을 캐싱하고, Provider를 통해 현재 테넌트의 DSLContext를 제공합니다.

### 구현

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

    @Bean
    @Qualifier("masterDslContext")
    public DSLContext masterDslContext(
            @Qualifier("baseConfiguration") org.jooq.Configuration baseConfiguration) {
        return DSL.using(baseConfiguration);
    }

    public static class TenantAwareDSLContextProvider {

        private final org.jooq.Configuration baseConfiguration;
        private final ConcurrentMap<String, org.jooq.Configuration> configurationCache;

        public TenantAwareDSLContextProvider(org.jooq.Configuration baseConfiguration) {
            this.baseConfiguration = baseConfiguration;
            this.configurationCache = new ConcurrentHashMap<>();
        }

        /**
         * 현재 테넌트의 DSLContext 반환
         */
        public DSLContext get() {
            String tenantId = TenantContext.getTenantId();
            return getForTenant(tenantId);
        }

        /**
         * 특정 테넌트의 DSLContext 반환 (cross-tenant 접근용)
         */
        public DSLContext getForTenant(String tenantId) {
            org.jooq.Configuration config = getConfiguration(tenantId);
            return DSL.using(config);
        }

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
         * 캐시 전체 초기화
         */
        public void clearCache() {
            configurationCache.clear();
        }

        /**
         * 특정 테넌트 캐시 제거
         */
        public void evictTenant(String tenantId) {
            configurationCache.remove(tenantId);
        }
    }
}
```

### 사용법

```java
@Service
public class UserService {

    private static final String SCHEMA = "public";  // RenderMapping 입력 스키마
    private final TenantAwareDSLContextProvider dslProvider;

    public UserService(TenantAwareDSLContextProvider dslProvider) {
        this.dslProvider = dslProvider;
    }

    // 현재 테넌트 데이터 조회 - 스키마 명시 필수!
    public Result<Record> findAllUsers() {
        return dslProvider.get()
                .select()
                .from(table(name(SCHEMA, "users")))
                .fetch();
    }

    // 특정 테넌트 데이터 조회 (cross-tenant)
    public Result<Record> findUsersForTenant(String tenantId) {
        return dslProvider.getForTenant(tenantId)
                .select()
                .from(table(name(SCHEMA, "users")))
                .fetch();
    }

    // 모든 테넌트 사용자 수 집계
    public Map<String, Integer> countUsersPerTenant(List<String> tenantIds) {
        return tenantIds.stream()
                .collect(Collectors.toMap(
                        tenantId -> tenantId,
                        tenantId -> dslProvider.getForTenant(tenantId)
                                .selectCount()
                                .from(table(name(SCHEMA, "users")))
                                .fetchOne(0, int.class)
                ));
    }
}
```

### 장점

1. **성능 최적화**
   - Configuration 객체를 테넌트별로 캐싱
   - DSLContext는 Configuration의 얇은 래퍼로 생성 비용 저렴
   - 프록시 오버헤드 없음

2. **넓은 사용 범위**
   - 웹 요청 외부에서도 사용 가능
   - `@Scheduled`, `@Async`, 배치 작업에서 정상 동작
   - 테스트 작성 용이

3. **Cross-tenant 접근**
   - `getForTenant(tenantId)`로 다른 테넌트 데이터 접근 가능
   - 관리자 기능, 데이터 마이그레이션 등에 유용

4. **캐시 관리**
   - `clearCache()`, `evictTenant()` 메서드 제공
   - 테넌트 스키마 변경 시 캐시 갱신 가능

5. **명시적인 코드**
   - `dslProvider.get()` 호출이 명시적
   - 테넌트 컨텍스트 의존성이 코드에서 드러남

### 단점

1. **약간 더 많은 코드**
   - `dsl.select()` 대신 `dslProvider.get().select()`
   - 한 단계 더 호출 필요

2. **메모리 사용**
   - 테넌트 수만큼 Configuration 객체가 메모리에 상주
   - 대부분의 경우 무시할 수 있는 수준 (Configuration은 가벼움)

---

## 비교 요약

| 항목 | Request Scope | Provider + Cache |
|------|---------------|------------------|
| **객체 생성** | 매 요청마다 Settings, Configuration, DSLContext | Configuration 캐싱, DSLContext만 생성 |
| **프록시** | CGLIB/JDK 프록시 사용 | 없음 (직접 호출) |
| **사용 범위** | 웹 요청 내부만 | 어디서든 가능 |
| **Cross-tenant** | 불가능 | `getForTenant()` 제공 |
| **캐시 관리** | 없음 | `clearCache()`, `evictTenant()` |
| **코드 복잡도** | 낮음 | 약간 높음 |
| **테스트 용이성** | 웹 컨텍스트 필요 | 단위 테스트 가능 |
| **디버깅** | 프록시로 인해 복잡 | 단순 |

---

## 캐싱이 안전한 이유

### Configuration의 불변성

jOOQ의 `Configuration.derive()` 메서드는 새로운 불변(immutable) 객체를 반환합니다:

```java
// derive()는 원본을 수정하지 않고 새 객체 반환
Configuration derived = baseConfiguration.derive(tenantSettings);

// baseConfiguration은 변경되지 않음
// derived도 생성 후 변경되지 않음
```

### 스레드 안전성

```java
private final ConcurrentMap<String, org.jooq.Configuration> configurationCache;

// ConcurrentHashMap.computeIfAbsent()는 원자적 연산
// 동시에 같은 테넌트 요청이 와도 Configuration은 한 번만 생성됨
configurationCache.computeIfAbsent(tenantId, this::createTenantConfiguration);
```

### DSLContext의 가벼움

```java
// DSLContext는 Configuration의 얇은 래퍼
// 생성 비용이 매우 저렴
return DSL.using(config);  // 거의 즉시 반환
```

---

## 권장 사항

### Provider + Cache 방식을 권장하는 경우

- 대부분의 프로덕션 환경
- 배치 작업이나 스케줄러가 있는 경우
- Cross-tenant 기능이 필요한 경우
- 성능이 중요한 경우
- 테스트 코드 작성이 많은 경우

### Request Scope 방식을 사용할 수 있는 경우

- 매우 단순한 웹 애플리케이션
- 프로토타입이나 PoC
- 기존 코드가 DSLContext 직접 주입을 사용하는 경우

---

## 마이그레이션 가이드

Request Scope에서 Provider 방식으로 전환:

### Before

```java
@Service
public class UserService {
    private final DSLContext dsl;

    public UserService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Result<Record> findAll() {
        return dsl.select().from(table(name("public", "users"))).fetch();
    }
}
```

### After

```java
@Service
public class UserService {
    private static final String SCHEMA = "public";
    private final TenantAwareDSLContextProvider dslProvider;

    public UserService(TenantAwareDSLContextProvider dslProvider) {
        this.dslProvider = dslProvider;
    }

    public Result<Record> findAll() {
        return dslProvider.get().select().from(table(name(SCHEMA, "users"))).fetch();
    }
}
```

변경 사항:
1. `DSLContext` → `TenantAwareDSLContextProvider`
2. `dsl.` → `dslProvider.get().`
3. 스키마 상수 추가 및 `table(name(SCHEMA, "tableName"))` 형태로 스키마 명시
