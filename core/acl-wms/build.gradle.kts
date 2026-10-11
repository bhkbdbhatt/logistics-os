plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

description = "L1 acl-wms: translates legacy WMS vendor feeds on legacy.wms.events.v1 to canonical scan/task/inventory events (obs.scan.v1, wms.task.events.v1, wms.inventory.events.v1)."

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
        include("io/logisticsos/obs/v1/*.avsc")
        include("io/logisticsos/wms/v1/*.avsc")
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