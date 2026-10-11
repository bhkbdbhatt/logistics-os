plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

description = "L1 ingest-gateway: terminates inbound observation streams, validates against canonical payload schemas, dedupes, and publishes canonical envelopes."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation(project(":platform:event-envelope"))
    implementation(project(":platform:kafka-commons"))
    implementation(project(":platform:idempotency"))
    implementation("org.apache.avro:avro")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Ship the schemas the service validates/publishes as classpath resources (schema.resource=true).
val schemasDir = layout.buildDirectory.dir("generated-schemas")
val copySchemas by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("contracts/avro")) {
        include("io/logisticsos/events/*.avsc")
        include("io/logisticsos/obs/v1/*.avsc")
        include("io/logisticsos/types/*.avsc")
        include("io/logisticsos/shipment/*.avsc")
    }
    into(schemasDir)
}
sourceSets.main {
    resources.srcDir(schemasDir)
}
tasks.processResources {
    dependsOn(copySchemas)
}