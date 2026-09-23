# syntax=docker/dockerfile:1

# Dependencies are resolved before the source is copied in, so editing a source file doesn't
# force Docker to re-download the whole Maven repository on every build.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# chmod: the wrapper loses its executable bit when the repo comes from a Windows checkout or a zip.
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -B clean package

# A JRE, not a JDK, runs the built jar: no compiler or build tools ship in the final image.
FROM eclipse-temurin:25-jre AS run
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
