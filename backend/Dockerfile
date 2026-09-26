# ---- build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI / locally (they need PostgreSQL); the image build only packages.
RUN mvn -B -q package -DskipTests

# ---- runtime -------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 ops && mkdir -p /var/lib/ops/documents && chown -R ops /var/lib/ops
WORKDIR /app
COPY --from=build /src/target/online-personalservices.jar app.jar
USER ops
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
