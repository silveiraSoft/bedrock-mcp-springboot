# syntax=docker/dockerfile:1.7
# =====================================================================================
#  bedrock-mcp-springboot - multi-stage, multi-arch (amd64 + arm64/Graviton) image
#  Build:  ./scripts/docker-build.sh          -> bedrock-mcp-springboot:<pom version>
#          docker build --build-arg APP_VERSION=0.1.0 -t bedrock-mcp-springboot:0.1.0 .
# =====================================================================================

# ---------- 1) Build ----------
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace

# Dependencies first -> cached layer while only sources change
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
ARG SKIP_TESTS=true
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests=${SKIP_TESTS} package \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

# ---------- 2) Runtime ----------
FROM eclipse-temurin:25-jre-noble
ARG APP_NAME=bedrock-mcp-springboot
ARG APP_VERSION=0.1.0

LABEL org.opencontainers.image.title="${APP_NAME}" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.description="NL gateway + MCP server over a Spring Boot REST API (Amazon Bedrock + Guardrails)" \
      org.opencontainers.image.source="https://github.com/<your-github-user>/bedrock-mcp-springboot"

# Non-root user (ECS/Fargate best practice; enables readonlyRootFilesystem with a tmpfs /tmp)
RUN groupadd --system --gid 10001 spring && useradd --system --uid 10001 --gid spring --no-create-home spring
WORKDIR /app

# Spring Boot layers: least -> most frequently changing
COPY --from=build --chown=spring:spring /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

ENV APP_VERSION=${APP_VERSION} \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
