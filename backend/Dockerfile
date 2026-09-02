# --- build ---------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, so a source-only change reuses the cached layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# --- runtime -------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system spi && useradd --system --gid spi spi
USER spi

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
