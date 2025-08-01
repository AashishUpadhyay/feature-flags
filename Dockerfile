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

# Copy the database scripts
COPY feature-flag-service/db ./db/

# Install required tools for database migration and health checks
RUN apk add --no-cache postgresql-client unzip bash curl

# Create directory for Liquibase artifacts
RUN mkdir -p db/artifacts

EXPOSE 8080

# Copy and set up the entrypoint script
COPY feature-flag-service/entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]