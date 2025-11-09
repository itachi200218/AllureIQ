# Use OpenJDK image
FROM openjdk:17-jdk-slim

# Set working directory inside the container
WORKDIR /app

# Copy the pom.xml and download dependencies first (for caching)
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins

# Copy all source code
COPY src ./src

# Package the application
RUN mvn -B clean package -DskipTests

# Expose the Render port dynamically
EXPOSE 8080

# Use environment variable PORT (Render injects it)
ENV PORT=8080

# Run the jar file
CMD ["sh", "-c", "java -jar target/*.jar --server.port=${PORT}"]
