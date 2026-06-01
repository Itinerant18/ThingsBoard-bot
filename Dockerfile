# Use OpenJDK 17 base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the built JAR file from target/ into the container
COPY target/ThingsBoard-Bot-0.0.1-SNAPSHOT.jar app.jar

# Expose the chatbot service port (default is 8083 for chat profile, 8080/8083 for others)
EXPOSE 8083

# Define the entrypoint to run the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]
