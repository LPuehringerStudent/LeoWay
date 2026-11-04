# Production image of the LeoWay API. Build from the repository root:
#   docker build -f backend/Dockerfile -t leoway-backend .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml dependency:go-offline
COPY backend backend
# Tests need Docker for PostgreSQL and already run in CI.
RUN mvn -B -q -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 leoway
WORKDIR /app
COPY --from=build --chown=leoway /src/backend/target/quarkus-app/ ./
USER leoway
ENV TZ=UTC QUARKUS_HTTP_HOST=0.0.0.0 QUARKUS_FLYWAY_MIGRATE_AT_START=true
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /health/live HTTP/1.0\r\n\r\n" >&3 && grep -q "200 OK" <&3'
CMD ["java", "-jar", "quarkus-run.jar"]
