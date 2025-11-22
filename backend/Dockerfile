# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------------------
# Stage 1 - build
# Dependencies are resolved in their own layer so that a source-only change does not
# re-download the world on every rebuild.
# ---------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Explode the fat jar into Spring Boot's layer directories. Dependencies change far less
# often than application classes, so extracting them separately keeps the image layers
# cacheable and makes a redeploy push only a few hundred kilobytes.
RUN java -Djarmode=layertools -jar target/crm-platform.jar extract --destination target/extracted

# ---------------------------------------------------------------------------------------
# Stage 2 - runtime
# ---------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is needed by HEALTHCHECK; tzdata keeps java.time honest about zone rules.
RUN apk add --no-cache curl tzdata \
    && addgroup -S -g 10001 crm \
    && adduser -S -u 10001 -G crm crm

WORKDIR /app

# Ordered least-volatile first.
COPY --from=build --chown=crm:crm /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=crm:crm /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=crm:crm /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=crm:crm /workspace/target/extracted/application/ ./

USER crm:crm

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then respects whatever the ECS task
# definition grants without the image having to know the number.
ENV SPRING_PROFILES_ACTIVE=docker \
    SERVER_PORT=8080 \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:MaxMetaspaceSize=192m -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# start-period covers JVM warm-up and Flyway migration on a cold database.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
