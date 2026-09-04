# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:21-jdk-jammy

# Set the working directory in the container
WORKDIR /app

# Copy the project files to the container
COPY . .

# Package the application
RUN chmod +x mvnw && ./mvnw clean package -Dmaven.test.skip=true

# Run the application
CMD ["java", "-jar", "Launcher/target/Launcher-0.1.0-exec.jar"]