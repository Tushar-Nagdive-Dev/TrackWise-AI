plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // For receiving async signals later (e.g., training/audit events)
    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }
