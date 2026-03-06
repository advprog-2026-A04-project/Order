# ===== build stage =====
FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY backend/ .
RUN gradle clean bootJar -x test

# ===== run stage =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]