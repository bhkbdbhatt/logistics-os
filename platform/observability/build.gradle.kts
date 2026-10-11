plugins {
    `java-library`
}

description = "MDC correlation + W3C tracecontext helpers for the Logistics OS services."

dependencies {
    api(libs.slf4j.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.slf4j.simple)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}