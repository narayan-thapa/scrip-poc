# syntax=docker/dockerfile:1
# Layered Spring Boot 4 image (Java 25). Layers are split so Docker caches dependencies
# separately from application code, keeping rebuilds small.

# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src ./src
# Skip the Docker-dependent integration slice during image build; unit/arch/ta4j gates run in CI.
RUN ./mvnw -B -q -DskipTests package

# ---- extract layers ----
FROM eclipse-temurin:25-jre AS extract
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app
# Order matters: least- to most-frequently-changing for optimal layer caching.
COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./
EXPOSE 8080
# Virtual threads + container-aware defaults come from the JVM/app config.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
