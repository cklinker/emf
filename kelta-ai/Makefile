.DEFAULT_GOAL := help

MVN := mvn

.PHONY: help setup build test verify dev lint format format-fix clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## Copy .env.example to .env if .env does not exist
	@[ -f .env ] || cp .env.example .env && echo ".env created — fill in secrets before running."

build: ## Compile without running tests
	$(MVN) clean package -DskipTests

test: ## Run unit tests
	$(MVN) test

verify: ## Full build + lint + tests (same as CI)
	$(MVN) verify

dev: ## Run the service locally (requires .env populated)
	$(MVN) spring-boot:run

lint: ## Run Checkstyle and Spotless check (no auto-fix)
	$(MVN) checkstyle:check spotless:check

format: ## Check formatting without applying changes
	$(MVN) spotless:check

format-fix: ## Apply Google Java Format via Spotless
	$(MVN) spotless:apply

clean: ## Remove build output
	$(MVN) clean
