# ---- build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Unit tests run in CI; the image build only packages.
RUN mvn -B -q package -DskipTests

# ---- runtime -------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 mock
WORKDIR /app
COPY --from=build /src/target/mock-erp.jar app.jar
USER mock
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
