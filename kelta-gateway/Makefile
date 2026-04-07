.PHONY: setup build test verify dev lint format format-fix clean

# Install dependencies and validate the project
setup:
	./mvnw validate

# Compile the project
build:
	./mvnw compile -DskipTests

# Run unit tests
test:
	./mvnw test

# Build, test, and verify (runs checkstyle + jacoco)
verify:
	./mvnw verify

# Run the gateway in dev mode (requires local dependencies running)
dev:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run Checkstyle linting
lint:
	./mvnw checkstyle:check

# Show formatting diff (Spotless check)
format:
	./mvnw spotless:check

# Apply Spotless formatting
format-fix:
	./mvnw spotless:apply

# Remove build artifacts
clean:
	./mvnw clean
