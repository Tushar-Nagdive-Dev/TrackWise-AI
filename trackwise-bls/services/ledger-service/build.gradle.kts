
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // Core web + JSON
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Data layer (we'll add entities/repositories later)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Observability (kept light for now)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Inter-service options (enable as you need)
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign") // Feign client to intelligence-service
    implementation("org.springframework.kafka:spring-kafka")                  // Kafka producer/consumer

    // API docs (Swagger UI at /swagger-ui.html when controllers exist)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
    // Resilience (circuit breaker around Feign/kafka ops)
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
