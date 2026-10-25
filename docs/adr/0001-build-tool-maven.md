# ADR-0001: Use Maven (with wrapper) as build tool

- **Status:** Accepted by owner (DISCOVERY-001 D3, session 002)
- **Date:** 2026-10-24

## Context
Spring Boot 4.1 + Spring AI 2.0 project, Java 25, built in GitHub Actions and Docker, deployed with Terraform.
Candidates: Maven, Gradle (Kotlin DSL). The owner is comfortable with both.

## Options considered
1. **Maven** — declarative, one obvious way; Spring AI/Spring Boot/AWS docs are Maven-first; BOM imports
   (`spring-ai-bom`, AWS SDK `bom`) are trivial; stable caching in GitHub Actions (`setup-java cache: maven`);
   Dependabot support is mature; fewer "build logic" bugs. Slower incremental builds.
2. **Gradle (Kotlin DSL)** — faster incremental/parallel builds, build cache, flexible; but more moving parts
   (plugin versions, configuration cache, Kotlin DSL upgrades) and build logic tends to grow.

## Decision
Maven 3.9.11 with a checked-in wrapper (`mvnw`, `mvnw.cmd`). For a single deployable service the extra speed of
Gradle does not outweigh Maven's predictability and documentation alignment.

## Consequences
- `./mvnw verify` is the single CI entry point. Docker builds use the official `maven:3.9.11-eclipse-temurin-25` image.
- Versions are pinned in `pom.xml` properties; AWS SDK version is kept equal to the one Spring AI is built with.

## Revisit when
The repo becomes a multi-module monorepo (several services/libraries) where build time dominates → re-evaluate Gradle.
