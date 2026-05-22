# Root Dockerfile — digunakan jika Render pointing ke repo root
FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY backend/ backend/
WORKDIR /app/backend
RUN gradle clean bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/build/libs/*.jar app.jar
EXPOSE 8080
# WAJIB: inject PORT dari Render
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
