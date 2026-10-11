plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

description = "tools:legacy-simulator: fake legacy TMS/WMS vendors emitting provider JSON onto legacy.tms.events.v1 and legacy.wms.events.v1 for local/CI smoke tests."

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.kafka:spring-kafka")
}

tasks.test {
    useJUnitPlatform()
}