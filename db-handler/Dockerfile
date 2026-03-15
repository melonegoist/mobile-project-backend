FROM gradle:8.5-jdk17 AS builder
WORKDIR /home/gradle/src

COPY build.gradle.kts settings.gradle.kts ./
COPY gradlew .
COPY gradle gradle
RUN gradle dependencies --no-daemon

COPY . .
RUN gradle clean build -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
