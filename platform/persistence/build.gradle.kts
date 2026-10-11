plugins {
    `java-library`
}

description = "Persistence building blocks: JDBC outbox + idempotency stores and shared Flyway migrations (ADR-0021)"

dependencies {
    api(project(":platform:outbox"))
    api(project(":platform:idempotency"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
