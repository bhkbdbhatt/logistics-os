# Third-Party Notices

Logistics OS incorporates the third-party components listed below. Each is licensed under its own
terms; the license shown is the primary license of the component as used by the release. This file
is a human-readable summary — the authoritative, exhaustive list for a given build is the release
**SBOM** produced by the image build (`.github/workflows/images.yml`, `sbom: true`).

> Maintainers: update this summary when dependencies in `gradle/libs.versions.toml` change. It is
> not a substitute for the SBOM.

## Application / platform

| Component | Version | License |
|-----------|---------|---------|
| Spring Boot | 3.4.1 | Apache-2.0 |
| Spring for Apache Kafka | (Boot-managed) | Apache-2.0 |
| Spring Data JPA / Hibernate ORM | (Boot-managed) | Apache-2.0 / LGPL-2.1 |
| Flyway (DB migrations) | (Boot-managed) | Apache-2.0 |
| Micrometer | (Boot-managed) | Apache-2.0 |
| Jackson (databind) | 2.18.2 | Apache-2.0 |
| SLF4J | 2.0.16 | MIT |
| Logback | (Boot-managed) | EPL-1.0 / LGPL-2.1 |
| PostgreSQL JDBC Driver | (Boot-managed) | BSD-2-Clause |

## Messaging / contracts

| Component | Version | License |
|-----------|---------|---------|
| Apache Kafka clients | (Boot-managed) | Apache-2.0 |
| Apache Avro | (see libs.versions.toml) | Apache-2.0 |
| Confluent Schema Registry client | (see libs.versions.toml) | Apache-2.0 |
| Confluent Schema Registry / Confluent Platform (if deployed) | — | Confluent Community License (not OSS) |

## Observability

| Component | Version | License |
|-----------|---------|---------|
| OpenTelemetry Java agent | 1.33.6 | Apache-2.0 |
| OpenTelemetry OTLP exporter / SDK | (managed) | Apache-2.0 |

## Build / test tooling (not distributed in runtime images)

| Component | Version | License |
|-----------|---------|---------|
| Gradle | 8.12.1 | Apache-2.0 |
| JUnit 5 | 5.10.3 | EPL-2.0 |
| AssertJ | 3.26.3 | Apache-2.0 |
| Testcontainers | (see libs.versions.toml) | MIT |

## Infrastructure / operations (not bundled; referenced by deployment assets)

| Component | Version | License |
|-----------|---------|---------|
| Eclipse Temurin JRE (base image) | 21 | GPL-2.0 WITH Classpath-exception-2.0 |
| Bitnami Kafka image (smoke test only) | 3.7.1 | Apache-2.0 (image terms apply) |
| Keycloak (identity, if deployed) | 26.x | Apache-2.0 |
| Debezium (CDC, if deployed) | 3.x | Apache-2.0 |
| Strimzi (Kafka operator, if used) | — | Apache-2.0 |
| Terraform AWS provider | ~> 5.0 | MPL-2.0 |
| kind | — | Apache-2.0 |
