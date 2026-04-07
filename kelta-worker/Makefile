.PHONY: setup build test verify dev lint format format-fix clean

MAVEN := mvn
WORKER_POM := kelta-worker/pom.xml
PLATFORM_POM := kelta-platform/pom.xml
RUNTIME_MODULES := runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema

# Detect if running from repo root or worker directory
ifneq ($(wildcard ../$(PLATFORM_POM)),)
    PLATFORM_POM_PATH := ../$(PLATFORM_POM)
    WORKER_POM_PATH := pom.xml
else
    PLATFORM_POM_PATH := $(PLATFORM_POM)
    WORKER_POM_PATH := $(WORKER_POM)
endif

## setup: Install toolchain (requires asdf)
setup:
	asdf install

## build: Compile runtime modules and package the worker (skips tests)
build:
	$(MAVEN) clean install -DskipTests -f $(PLATFORM_POM_PATH) \
		-pl $(RUNTIME_MODULES) -am -B
	$(MAVEN) clean package -DskipTests -f $(WORKER_POM_PATH) -B

## test: Run unit tests
test:
	$(MAVEN) test -f $(WORKER_POM_PATH) -B

## verify: Full build + test + lint
verify:
	$(MAVEN) clean install -DskipTests -f $(PLATFORM_POM_PATH) \
		-pl $(RUNTIME_MODULES) -am -B
	$(MAVEN) verify -f $(WORKER_POM_PATH) -B

## dev: Run the worker locally with Spring Boot devtools
dev:
	$(MAVEN) spring-boot:run -f $(WORKER_POM_PATH)

## lint: Run Checkstyle
lint:
	$(MAVEN) checkstyle:check -f $(WORKER_POM_PATH) -B

## format: Check code formatting with Spotless
format:
	$(MAVEN) spotless:check -f $(WORKER_POM_PATH) -B

## format-fix: Apply Spotless formatting
format-fix:
	$(MAVEN) spotless:apply -f $(WORKER_POM_PATH) -B

## clean: Remove build artifacts
clean:
	$(MAVEN) clean -f $(WORKER_POM_PATH) -B
