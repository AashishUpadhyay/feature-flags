#!/bin/bash

# Database connection details
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-feature_flag_service_db}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

# Liquibase details
CHANGELOG_FILE="db/changelog/db-changelog-master.xml"
LIQUIBASE_VERSION="4.19.0"

# Wait for database to be ready
wait_for_db() {
    echo "Waiting for database to be ready..."
    until pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" > /dev/null 2>&1; do
        echo "Database is not ready. Retrying in 2 seconds..."
        sleep 2
    done
    echo "Database is ready!"
}

# Run Liquibase migrations
run_migrations() {
    echo "Running database migrations..."
    mvn liquibase:update \
        -Dliquibase.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} \
        -Dliquibase.username=${DB_USER} \
        -Dliquibase.password=${DB_PASSWORD} \
        -Dliquibase.changeLogFile=${CHANGELOG_FILE}
}

# Main execution
main() {
    wait_for_db
    run_migrations
}

main