plugins {
    `java-library`
}

description = "Licensing + feature entitlement seam (soft now, signed license later) for the Logistics OS editions."

dependencies {
    api(libs.jackson.databind)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}