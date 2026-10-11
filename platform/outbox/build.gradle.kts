plugins {
    `java-library`
}

description = "Transactional outbox relay helpers (ADR-0007)."

dependencies {
    api(project(":platform:idempotency"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}