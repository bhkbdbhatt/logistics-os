plugins {
    `java-library`
}

description = "Multi-tenant identity + thread-local context for the Logistics OS event backbone."

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}