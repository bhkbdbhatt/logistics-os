plugins {
    `java-library`
}

description = "Universal EventEnvelope model, builder, ULID ids and Avro codec (io.logisticsos.events)."

dependencies {
    api(libs.avro)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Keep contracts/ as source of truth: expose EventEnvelope.avsc to tests without duplicating it.
val schemasDir = layout.buildDirectory.dir("generated-schemas")
val copySchemas by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("contracts/avro")) {
        include("io/logisticsos/events/*.avsc")
    }
    into(schemasDir)
}
sourceSets.test {
    resources.srcDir(schemasDir)
}
tasks.test {
    dependsOn(copySchemas)
}