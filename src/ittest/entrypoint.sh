#!/bin/sh

echo "running integration tests..."
echo "API_HOST: $API_HOST"
echo "API_PORT: $API_PORT"

# Run tests with gotestsum for better output and junit reporting
gotestsum --junitfile data/integration-tests.xml \
        --format=standard-verbose \
        --junitfile-testsuite-name=full \
        --junitfile-testcase-classname=full \
        -- -v ./...