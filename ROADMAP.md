# Project Roadmap & Improvement Spec

This document tracks planned improvements, bug fixes, and architectural evolutions for `javaagent-instrumentation`. Each item defines the problem, proposed solution, and acceptance criteria.

---

## Progress Overview

- [x] **1. Exception Handling in Instrumented Methods** *(High Priority / Bug Fix)*
- [ ] **2. Configurable Duration Threshold** *(High Priority / Usability)*
- [ ] **3. Wildcard & Package Prefix Matching** *(High Priority / Usability)*
- [ ] **4. Shaded Dependency Relocation** *(Medium Priority / Safety)*
- [ ] **5. Call Depth & Hierarchy Indentation** *(Medium Priority / UX)*
- [ ] **6. Structured & Configurable Output Format (JSON / Sinks)** *(Medium Priority / UX)*
- [ ] **7. Support for Modern Java Editions & Tooling** *(Long-term / Modernization)*
- [ ] **8. Dynamic JVM Attachment (`agentmain`)** *(Long-term / Advanced)*

---

## Detailed Specifications

### 1. Exception Handling in Instrumented Methods (`asFinally`)
* **Status**: Completed
* **Priority**: High (Bug Fix)
* **Problem**: In [`DurationTransformer.java`](src/main/java/mvd/instrumentation/DurationTransformer.java), `target.insertAfter(code)` only injects callback code before regular `return` opcodes. If a method throws an exception (checked or runtime), the timing callback is skipped. Methods that fail or perform slow operations leading to an exception are completely invisible.
* **Proposed Solution**:
  * Use Javassist's `target.insertAfter(code, true)`. The `true` parameter compiles the timing logic into a `try ... finally` block.
  * Use a zero-allocation `ThreadLocal<LongStack>` (initial capacity 32, primitive `long[]`) with a `SENTINEL` marker (`Long.MIN_VALUE`) at the bottom to prevent StackMapTable `VerifyError` and avoid underflow desynchronization.
  * Enable the existing test `reportsDurationWhenMethodThrows()` in [`DurationTransformerTest.java`](src/test/java/mvd/instrumentation/DurationTransformerTest.java) by removing the `@Ignore` annotation.
* **Acceptance Criteria**:
  * Methods exiting exceptionally trigger `registerStartTime` with accurate execution duration.
  * All unit tests pass (`make test`).

---

### 2. Configurable Duration Threshold
* **Status**: Open
* **Priority**: High (Usability)
* **Problem**: The 50 millisecond threshold is hardcoded as `private static final int MINIMAL_INVOCATION_TIME_MILLIS = 50` in [`DurationAgent.java`](src/main/java/mvd/instrumentation/DurationAgent.java). Users cannot tune the threshold for different performance profiling needs without recompiling.
* **Proposed Solution**:
  * Support passing threshold via agent arguments:
    * Format: `-javaagent:agent.jar=classes.cfg;threshold=100` or `-javaagent:agent.jar=classes.cfg,threshold=100`.
  * Support fallback to a system property: `-Dduration.threshold=100`.
  * Fall back to default (50ms) if not specified or invalid.
* **Acceptance Criteria**:
  * Parsing accepts configuration file path + optional threshold.
  * Invocations taking between `1` and `N` ms are logged according to the configured threshold.
  * Unit tests cover argument parsing with valid, invalid, and missing thresholds.

---

### 3. Wildcard & Package Prefix Matching
* **Status**: Open
* **Priority**: High (Usability)
* **Problem**: [`classes.cfg`](classes.cfg) currently requires listing every individual fully qualified class name. There is no support for package wildcards (e.g. `com.example.service.*` or `com.example.**`). In addition, blank lines and comment lines (`# ...`) in `classes.cfg` are not filtered, causing empty strings to be added to the whitelist.
* **Proposed Solution**:
  * Ignore blank lines, trailing/leading whitespace, and `#` comments when reading `classes.cfg`.
  * Convert whitelist entries into predicates supporting:
    * Exact match: `com.example.Foo`
    * Single-level wildcard: `com.example.*` (matches direct classes in package)
    * Recursive prefix wildcard: `com.example.**` (matches all subpackages)
* **Acceptance Criteria**:
  * Classes in matched subpackages are instrumented without listing each class individually.
  * Blank lines and `#` comments in `classes.cfg` do not break instrumentation.
  * Unit tests verify exact, wildcard, and recursive wildcard matching.

---

### 4. Shaded Dependency Relocation (`pom.xml`)
* **Status**: Open
* **Priority**: Medium (Safety)
* **Problem**: The `maven-shade-plugin` packages `org.javassist:javassist` into the agent fat JAR without package relocation. If the target application being profiled also uses Javassist (e.g. Hibernate, Quarkus), classpath conflicts or `LinkageError` can occur.
* **Proposed Solution**:
  * Add `<relocations>` to `maven-shade-plugin` in [`pom.xml`](pom.xml):
    ```xml
    <relocations>
        <relocation>
            <pattern>javassist</pattern>
            <shadedPattern>mvd.instrumentation.shaded.javassist</shadedPattern>
        </relocation>
    </relocations>
    ```
* **Acceptance Criteria**:
  * Packaged JAR contains Javassist classes under `mvd.instrumentation.shaded.javassist.*`.
  * Agent operates without interfering with host application dependencies.

---

### 5. Call Depth & Hierarchy Indentation
* **Status**: Open
* **Priority**: Medium (UX)
* **Problem**: When instrumented method `A()` calls instrumented method `B()`, both are printed at the same indentation level. It is difficult to discern whether `B()` is a standalone call or part of `A()`'s execution tree.
* **Proposed Solution**:
  * Maintain a `ThreadLocal<Integer>` call depth counter in the agent runtime.
  * Increment depth at method entry, decrement at method exit.
  * Indent output based on call depth (e.g. two spaces per depth level or ASCII tree markers `└──`).
* **Acceptance Criteria**:
  * Nested calls appear indented under parent methods in stdout.
  * Call depth counter is thread-safe and properly decremented even on exceptions.

---

### 6. Structured & Configurable Output Format (JSON & Sinks)
* **Status**: Open
* **Priority**: Medium (UX / Integration)
* **Problem**: Output is currently hardcoded to `System.out.println` in plain text format (`Method.name() : 123`). This pollutes stdout for applications relying on structured stdout logging and cannot easily be ingested by log aggregators (Elasticsearch, Datadog, Prometheus). In addition, `System.out.println` synchronizes on `PrintStream`, which can cause thread contention.
* **Proposed Solution**:
  * **Structured Format (JSON)**:
    * Support JSON line output mode (`{"timestamp": 1696871234, "thread": "main", "method": "...", "durationMs": 120}`).
  * **Configurable Sinks**:
    * Support writing output to a file (e.g. `-Dduration.output=file:///tmp/duration.log`) or stderr instead of stdout.
    * Use non-blocking or lightweight buffered writing to avoid thread contention.
* **Acceptance Criteria**:
  * Users can select between `text` and `json` output formats.
  * Output can be redirected to a dedicated log file.

---

### 7. Support for Modern Java Editions & Tooling
* **Status**: Open
* **Priority**: Long-term (Modernization)
* **Problem**: The project is currently configured for Java 8 (`<source>1.8</source>`, `<target>1.8</target>`), uses Javassist 3.22 (from 2017), and JUnit 4.12. Modern Java editions (Java 17, 21, 25 LTS) introduce the Java Module System (JPMS), strong encapsulation (`--add-opens`), nestmates, records, and virtual threads (Project Loom). Javassist has known limitations with modern bytecode features.
* **Proposed Solution**:
  * Update Maven compiler and surefire plugins to modern versions.
  * Migrate from JUnit 4 to JUnit 5 (Jupiter).
  * Update bytecode engine:
    * Upgrade to latest Javassist (3.30+) or migrate to **Byte Buddy**, the industry-standard instrumentation library designed for modern JVMs (native JPMS support, invokedynamic, records, virtual thread safety).
  * Support building and running across multi-release targets (Java 8, 11, 17, 21+).
* **Acceptance Criteria**:
  * Clean build and tests running on Java 17 and 21 without JVM warnings or access flags needed.

---

### 8. Dynamic JVM Attachment (`agentmain`)
* **Status**: Open
* **Priority**: Long-term (Advanced)
* **Problem**: The agent only implements `premain` and must be declared at JVM startup (`-javaagent:...`). Profiling a running production application requires restarting it.
* **Proposed Solution**:
  * Implement `agentmain(String agentArgs, Instrumentation inst)`.
  * Enable `Can-Retransform-Classes: true` in `MANIFEST.MF`.
  * Use `inst.retransformClasses(...)` on already loaded classes matching the whitelist.
* **Acceptance Criteria**:
  * The agent JAR can be attached to a running JVM via `jcmd <pid> JVMTI.agent_load <jar>=classes.cfg` or the Attach API without restarting the process.
