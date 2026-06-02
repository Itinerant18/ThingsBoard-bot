# Stage 1: Build React Frontend
FROM node:18-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot Backend
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
# Copy the compiled static assets from Stage 1 into the src/main/resources/static directory
COPY --from=frontend-builder /app/src/main/resources/static/ ./src/main/resources/static/
RUN mvn clean package -DskipTests

# Stage 3: Packaging JVM JRE runner
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=backend-builder /app/target/ThingsBoard-Bot-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
