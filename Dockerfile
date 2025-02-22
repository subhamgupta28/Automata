# Use the official OpenJDK image to build the application
FROM openjdk:17-jdk-slim as builder

# Set the working directory
WORKDIR /app

# Copy the built JAR file into the container
COPY target/myapp-0.0.1-SNAPSHOT.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
