IMAGE     ?= maven:3-eclipse-temurin-11
M2_CACHE  ?= $(HOME)/.m2
WORKSPACE ?= $(shell pwd)
JAVA_OPTS ?=

DOCKER_RUN = docker run --rm \
	--user $(shell id -u):$(shell id -g) \
	-v "$(WORKSPACE)":/workspace \
	-w /workspace \
	-e MAVEN_CONFIG=/var/maven/.m2 -e MAVEN_OPTS=-Duser.home=/var/maven -v "$(M2_CACHE)":/var/maven/.m2 \
	$(IMAGE)

.PHONY: all check-docker test package clean compile demo bench help

all: test

check-docker:
	@docker info >/dev/null 2>&1 || (echo "Error: Docker/Colima daemon is not running. Try: colima start" >&2 && exit 1)

test: check-docker
	$(DOCKER_RUN) mvn test $(ARGS)
	@scripts/coverage-summary.sh

package: check-docker
	$(DOCKER_RUN) mvn package $(ARGS)

compile: check-docker
	$(DOCKER_RUN) mvn compile $(ARGS)

clean: check-docker
	$(DOCKER_RUN) mvn clean $(ARGS)

demo: check-docker
	$(DOCKER_RUN) mvn test-compile package -DskipTests -B
	$(DOCKER_RUN) java $(JAVA_OPTS) -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=demo.cfg \
		-cp target/test-classes:target/classes \
		mvd.demo.DemoApp

bench: check-docker
	$(DOCKER_RUN) mvn test-compile exec:java -Dexec.mainClass="mvd.instrumentation.CoreClassFilterBenchmark" $(ARGS)

help:
	@echo "Available targets:"
	@echo "  make         - Run unit tests (default)"
	@echo "  make test    - Run unit tests (pass ARGS='-Dtest=...' for specific test)"
	@echo "  make package - Build the shaded agent JAR"
	@echo "  make bench   - Run JMH microbenchmarks for CoreClassFilter"
	@echo "  make demo    - Build and run the interactive showcase demo (pass JAVA_OPTS='-Dmvd.instrumentation.report.format=json')"
	@echo "  make clean   - Clean the build artifacts"
	@echo "  make compile - Compile main source files"

