FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY ws-server ./ws-server
COPY ws-client ./ws-client
RUN ./gradlew :ws-client:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/ws-client/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
