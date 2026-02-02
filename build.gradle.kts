plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("nu.studer.jooq") version "9.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Docker Compose Support
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // jOOQ Code Generation (optional - for generating type-safe classes)
    jooqGenerator("org.postgresql:postgresql")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// jOOQ code generation configuration
// This is optional - the project uses dynamic DSL (DSL.table(name("...")))
// Run manually: ./gradlew generateJooq (requires running PostgreSQL)
jooq {
    version.set("3.19.13")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false) // Don't auto-generate on compile
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/multitenancy_db"
                    user = "app_user"
                    password = "app_password"
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isPojos = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "com.example.multitenancy.jooq.generated"
                        directory = "build/generated-src/jooq/main"
                    }
                }
            }
        }
    }
}

// Add generated sources to source sets (only if generated)
sourceSets {
    main {
        java {
            srcDir("build/generated-src/jooq/main")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
