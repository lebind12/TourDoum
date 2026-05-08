# Spring Boot backend — multi-stage build.
# INFRA-AZ-2 ADR-0013 §8 prod-lite. ACR Basic 에 push, ACA 가 SystemAssigned MI 로 pull.
#
# build:
#   docker build -t tourdoum-backend:dev backend/
# run:
#   docker run -p 30080:30080 tourdoum-backend:dev

ARG JAVA_VERSION=21

# ---------- builder ----------
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS builder
WORKDIR /workspace

# Maven wrapper + deps cache layer
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q dependency:go-offline

# source
COPY src src
COPY checkstyle-suppressions.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests -DskipITs package \
    && cp target/tourdoum-backend-*.jar app.jar

# ---------- runtime ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime
WORKDIR /app

# non-root user
RUN groupadd -g 1001 spring && useradd -u 1001 -g spring -s /bin/false spring
USER spring:spring

COPY --from=builder --chown=spring:spring /workspace/app.jar /app/app.jar

# ACA targetPort=30080 (infra/azure/bicep container-app.bicep)
EXPOSE 30080
ENV SERVER_PORT=30080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:30080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
