# Use an official OpenJDK runtime as a parent image
FROM openjdk:21-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the project files to the container
COPY . .

# Package the application
RUN ./mvnw package

# Run the application
CMD ["java", "-jar", "Launcher/target/Launcher-0.0.1-SNAPSHOT.jar"]