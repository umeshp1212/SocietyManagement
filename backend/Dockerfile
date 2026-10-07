# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# gosu lets the entrypoint start as root (to fix mounted-volume ownership) and
# then drop privileges to the non-root appuser before launching the JVM.
RUN apt-get update \
    && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN mkdir -p /app/uploads /app/logs && chown -R appuser:appuser /app

COPY --from=build /app/target/society-management-1.0.0-SNAPSHOT.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# NOTE: intentionally NOT setting `USER appuser` here. The entrypoint starts as
# root to repair ownership of the mounted volumes, then uses gosu to run the JVM
# as appuser. This keeps the application process non-root while allowing the
# one-time ownership fixup.

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
