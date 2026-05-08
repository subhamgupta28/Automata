# Stage 1: Build Frontend
FROM node:20-slim AS ui-builder
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Backend
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /build
# Copy UI build results to static resources
COPY --from=ui-builder /build/src/main/resources/static ./src/main/resources/static
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 3: Run Application
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=backend-builder /build/target/Automata-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8010
ENTRYPOINT ["java", "-jar", "app.jar"]