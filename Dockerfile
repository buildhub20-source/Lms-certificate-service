# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# The health check needs an HTTP client; do not rely on the base image shipping one.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system cert && useradd --system --gid cert --create-home cert

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R cert:cert /app

USER cert
EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8081/actuator/health | grep -q UP"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
