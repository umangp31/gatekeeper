# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies first: only re-resolve when the POM changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline || true

# Build the layered jar. Tests need Docker (Testcontainers) which isn't available
# in the image build; CI (T-37) runs `mvnw verify` before any image is published.
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# Extract Spring Boot layers for better runtime layer caching.
RUN java -Djarmode=layertools -jar target/gatekeeper-1.0-SNAPSHOT.jar extract --destination target/extracted

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN addgroup -S app && adduser -S app -G app
USER app

# Ordered most- to least-stable so unchanged layers stay cached.
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

EXPOSE 8080

# Koyeb free tier: 512 MB / 0.1 vCPU. Cap heap so the JVM leaves room for
# metaspace, threads and off-heap without being OOM-killed.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
