FROM eclipse-temurin:11-jdk-alpine

WORKDIR /app

# Copy the Maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy the source code
COPY src ./src/

# Build the application
RUN ./mvnw package -DskipTests

# Install curl for healthchecks
RUN apk add --no-cache curl

EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/target/feature-flags-1.0-SNAPSHOT.jar"]