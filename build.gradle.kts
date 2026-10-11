import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.dependency.management) apply false
}

group = "io.logisticsos"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://packages.confluent.io/maven/") }
    }
}

subprojects {
    if (name in setOf("event-envelope", "kafka-commons", "outbox", "idempotency", "tenant", "observability")) {
        apply(plugin = "java-library")
    }
}

// Default Java toolchain for every JVM module.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// Kotlin modules (L5 and shared code) target JVM 21.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }
}