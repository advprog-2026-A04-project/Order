# ---- Build stage ----
FROM gradle:8.8-jdk21 AS build
WORKDIR /app

COPY backend/ backend/
WORKDIR /app/backend

RUN gradle clean bootJar --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/backend/build/libs/*.jar app.jar

# Render inject PORT env. Spring MUST listen to it.
EXPOSE 8080
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]