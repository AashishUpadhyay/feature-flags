.PHONY: build clean run test package help

# Default target
.DEFAULT_GOAL := help

# Variables
MAVEN := ./mvnw
ifeq ($(OS),Windows_NT)
    MAVEN := mvnw.cmd
endif

# Colors for help target
YELLOW := \033[1;33m
NC := \033[0m # No Color

# Targets
help: ## Show this help message
	@echo 'Usage:'
	@echo '  make [target]'
	@echo ''
	@echo 'Targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  ${YELLOW}%-15s${NC} %s\n", $$1, $$2}' $(MAKEFILE_LIST)

clean: ## Clean the project (remove target directory)
	@echo "Cleaning project..."
	@mvn clean

build: ## Compile the project
	@echo "Building project..."
	@mvn clean compile

test: ## Run tests
	@echo "Running tests..."
	@mvn test

package: clean ## Create JAR package
	@echo "Creating JAR package..."
	@mvn package

run: ## Run the application
	@echo "Running application..."
	@mvn spring-boot:run

install-maven-wrapper: ## Install Maven Wrapper
	@echo "Installing Maven Wrapper..."
	@mvn -N wrapper:wrapper

verify: ## Run verify phase (includes tests)
	@echo "Running verify phase..."
	@mvn verify

dependency-updates: ## Check for dependency updates
	@echo "Checking for dependency updates..."
	@mvn versions:display-dependency-updates

format: ## Format code using Google Java Format
	@echo "Formatting code..."
	@mvn com.coveo:fmt-maven-plugin:format