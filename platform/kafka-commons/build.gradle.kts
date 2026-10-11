plugins {
    `java-library`
}

description = "Kafka Serde + routing helpers for the EventEnvelope (io.logisticsos.events.kafka)."

dependencies {
    api(project(":platform:event-envelope"))
    api(libs.kafka.clients)
    api(libs.confluent.schema.registry.client)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Expose the envelope schema to tests without duplicating it (preserves the contracts tree layout).
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