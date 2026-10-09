IMAGE     ?= maven:3-eclipse-temurin-11
M2_CACHE  ?= $(HOME)/.m2
WORKSPACE ?= $(shell pwd)

DOCKER_RUN = docker run --rm \
	-v "$(WORKSPACE)":/workspace \
	-w /workspace \
	-v "$(M2_CACHE)":/root/.m2 \
	$(IMAGE)

.PHONY: all test package clean compile help

all: test

check-docker:
	@docker info >/dev/null 2>&1 || (echo "Error: Docker/Colima daemon is not running. Try: colima start" >&2 && exit 1)

test: check-docker
	$(DOCKER_RUN) mvn test $(ARGS)

package: check-docker
	$(DOCKER_RUN) mvn package $(ARGS)

compile: check-docker
	$(DOCKER_RUN) mvn compile $(ARGS)

clean: check-docker
	$(DOCKER_RUN) mvn clean $(ARGS)

help:
	@echo "Available targets:"
	@echo "  make         - Run unit tests (default)"
	@echo "  make test    - Run unit tests (pass ARGS='-Dtest=...' for specific test)"
	@echo "  make package - Build the shaded agent JAR"
	@echo "  make clean   - Clean the build artifacts"
	@echo "  make compile - Compile main source files"
