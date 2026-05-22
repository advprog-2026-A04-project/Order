# ---- Build stage ----
FROM gradle:8.8-jdk21 AS build
WORKDIR /app

COPY backend/ backend/
WORKDIR /app/backend

RUN gradle clean bootJar --no-daemon -x test

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/backend/build/libs/*.jar app.jar

# Render injects PORT env var (usually 10000, not 8080).
# Pass it explicitly to Spring via -Dserver.port so the app
# binds to the correct port and Render's health check passes.
EXPOSE 8080
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]