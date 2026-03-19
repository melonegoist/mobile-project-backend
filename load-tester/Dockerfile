FROM eclipse-temurin:24-jdk-alpine AS builder
WORKDIR /home/gradle/src

RUN apk add --no-cache bash

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY . .
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
