// trackwise-bls/build.gradle.kts
plugins {
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

dependencies {
    // Keep versions consistent with your root (adjust as needed)
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.3"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2024.0.0"))
    implementation(platform("io.github.resilience4j:resilience4j-bom:2.3.0"))

    // now add deps WITHOUT versions; they’ll come from the BOMs
    api("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }
