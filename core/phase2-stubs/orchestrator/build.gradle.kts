plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

description = "L5 phase2-stubs/orchestrator: minimal Phase-2 command stub. Boots, builds idempotent orch.command.v1 commands (NOTIFY_CUSTOMER), and emits them on a schedule or via POST /orch/commands. Intentionally NOT the real orchestrator (workflow engine, sagas, decision routing are 0.3.0+)."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
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
        include("io/logisticsos/types/*.avsc")
        include("io/logisticsos/orchestration/v1/*.avsc")
    }
    into(schemasDir)
}
sourceSets.main {
    resources.srcDir(schemasDir)
}
tasks.processResources {
    dependsOn(copySchemas)
}