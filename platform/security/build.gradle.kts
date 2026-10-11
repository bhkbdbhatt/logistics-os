plugins {
    `java-library`
    alias(libs.plugins.dependency.management)
}

description = "OAuth2/OIDC resource-server wiring + local-JSON dev mode; binds TenantContext from token claims (ADR-0020)"

dependencies {
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.oauth2.resource.server)
    api(project(":platform:tenant"))
    implementation(libs.jackson.databind)
    compileOnly(libs.jakarta.servlet.api)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

tasks.test {
    useJUnitPlatform()
}
