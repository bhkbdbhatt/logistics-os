plugins {
    `java-library`
}

description = "Consumer-side dedupe + version-guard helpers (docs/schemas/common-types.md 4.2)."

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}