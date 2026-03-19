FROM gradle:8.14-jdk21 AS builder
WORKDIR /app

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/build/install/mobile-handler/ .
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 9090
ENTRYPOINT ["bin/mobile-handler"]
