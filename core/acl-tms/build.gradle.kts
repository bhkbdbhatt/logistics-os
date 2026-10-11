plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

description = "L1 acl-tms: translates legacy TMS vendor feeds on legacy.tms.events.v1 to canonical shipment events on tms.shipment.events.v1."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.kafka:spring-kafka")
    implementation(project(":platform:event-envelope"))
    implementation(project(":platform:kafka-commons"))
    implementation("org.apache.avro:avro")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val schemasDir = layout.buildDirectory.dir("generated-schemas")
val copySchemas by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("contracts/avro")) {
        include("io/logisticsos/events/*.avsc")
        include("io/logisticsos/shipment/*.avsc")
        include("io/logisticsos/shipment/v1/*.avsc")
        include("io/logisticsos/types/*.avsc")
    }
    into(schemasDir)
}
sourceSets.main {
    resources.srcDir(schemasDir)
}
tasks.processResources {
    dependsOn(copySchemas)
}