.PHONY: build test verify dev clean lint format setup

## Setup development environment
setup:
	mvn dependency:resolve

## Build the project
build:
	mvn clean package -DskipTests

## Run unit tests
test:
	mvn test

## Run all tests including integration
verify:
	mvn verify -Pintegration-tests

## Run locally
dev:
	mvn spring-boot:run

## Run checkstyle linter
lint:
	mvn checkstyle:check

## Check code formatting
format:
	mvn spotless:check

## Apply code formatting
format-fix:
	mvn spotless:apply

## Clean build artifacts
clean:
	mvn clean
