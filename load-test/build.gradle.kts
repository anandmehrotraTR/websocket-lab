plugins {
    id("org.springframework.boot") version "3.3.13"
    id("io.spring.dependency-management")
    java
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    // Async HTTP client for REST API calls
    implementation("org.asynchttpclient:async-http-client:3.0.0")

    // Metrics and monitoring
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Logging
    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic")

    // CLI argument parsing
    implementation("info.picocli:picocli:4.7.6")

    // Apache Commons for statistics
    implementation("org.apache.commons:commons-math3:3.6.1")

    // HdrHistogram for latency percentiles
    implementation("org.hdrhistogram:HdrHistogram:2.2.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("boot")
    mainClass.set("com.websocketlab.loadtest.LoadTestApplication")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
