# Project Roadmap & Improvement Spec

This document tracks planned improvements, bug fixes, and architectural evolutions for `javaagent-instrumentation`. Each item defines the problem, proposed solution, and acceptance criteria.

---

## Progress Overview

- [x] **1. Exception Handling in Instrumented Methods** *(High Priority / Bug Fix)*
- [x] **2. Configurable Duration Threshold** *(High Priority / Usability)*
- [x] **3. Wildcard & Package Prefix Matching** *(High Priority / Usability)*
- [ ] **4. Shaded Dependency Relocation** *(Medium Priority / Safety)*
- [x] **5. Call Depth & Hierarchy Indentation** *(Medium Priority / UX)*
- [x] **6. Structured & Configurable Output Format (JSON / Sinks)** *(Medium Priority / UX)*
- [ ] **7. Support for Modern Java Editions & Tooling** *(Long-term / Modernization)*
- [ ] **8. Dynamic JVM Attachment (`agentmain`)** *(Long-term / Advanced)*
- [ ] **9. Fix Agent Loading & Classloader Isolation** *(Medium Priority / Architecture)*
- [x] **10. Internal Agent Logging Facility & System Property Namespace Cleanup** *(Medium Priority / Usability)*
- [ ] **11. Agent Overhead Benchmarks & Performance Regression Gates** *(High Priority / Performance)*

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
* **Status**: Completed
* **Priority**: High (Usability)
* **Problem**: The 50 millisecond threshold was hardcoded as `private static final int MINIMAL_INVOCATION_TIME_MILLIS = 50` in [`DurationAgent.java`](src/main/java/mvd/instrumentation/DurationAgent.java). Users could not tune the threshold for different performance profiling needs without recompiling.
* **Implemented Solution**:
  * Configured via system property: `-Dmvd.instrumentation.agent.threshold=<ms>` (e.g. `-Dmvd.instrumentation.agent.threshold=100`).
  * Integrated directly into [`ReportConfig.java`](src/main/java/mvd/instrumentation/ReportConfig.java), keeping `agentArgs` dedicated solely to the configuration file path without brittle custom delimiter parsing.
  * Defaults to `50` ms if not specified. Non-numeric or negative values stop the agent at startup with an `IllegalArgumentException`, like the other properties.
  * Caps values exceeding `180_000` ms (3 minutes) to 180,000 ms with a warning, so an accidental huge value (e.g. a day) cannot silently disable recording.
* **Acceptance Criteria**:
  * Setting `-Dmvd.instrumentation.agent.threshold` adjusts the minimum execution duration recorded by `Recorder`.
  * Unit tests cover missing, valid, zero, capped, negative, and non-numeric thresholds.

---

### 3. Wildcard & Package Prefix Matching
* **Status**: Completed
* **Priority**: High (Usability)
* **Problem**: [`classes.cfg`](classes.cfg) previously required listing every individual fully qualified class name. There was no support for package wildcards (e.g. `com.example.service.*` or `com.example.**`). In addition, blank lines and comment lines (`# ...`) in `classes.cfg` were not filtered, causing empty strings to be added to the whitelist.
* **Implemented Solution**:
  * Implemented [`ClassFilter.java`](src/main/java/mvd/instrumentation/ClassFilter.java) implementing `Predicate<String>` with fast-path, zero-heap-allocation evaluation:
    * Exact match: `com.example.Foo` via `Set<String>` lookup.
    * Single-level wildcard: `com.example.*` via string prefix match (`com/example/`) + ensuring no further `/` delimiter.
    * Recursive prefix wildcard: `com.example.**` via package prefix match (`com/example/`).
    * General globs: fallback to compiled `java.util.regex.Pattern`.
  * Sanitizes configuration inputs by stripping full-line and inline `#` comments, trimming whitespace, and ignoring empty/blank lines.
  * Never matches JDK (`java/`, `javax/`, `jdk/`, `sun/`, `com/sun/`), javassist or agent classes, and rejects a bare `**`; a pattern covering excluded classes logs a warning. `DurationTransformer` also skips bootstrap-loaded classes. Instrumenting those classes aborted JVM startup (`**`) or silently lost the report (`java.util.**`), because they cannot see the agent classes the instrumentation calls.
  * Added JMH microbenchmark suite ([`ClassFilterBenchmark.java`](src/test/java/mvd/instrumentation/ClassFilterBenchmark.java)) runnable via `make bench`, showing nanosecond-range evaluation (`~4.7ns` for exact match hits, `~6.5ns` for recursive wildcard hits, `~6.8ns` for JDK class misses).
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
* **Status**: Completed
* **Priority**: Medium (UX)
* **Problem**: When instrumented method `A()` calls instrumented method `B()`, both are printed at the same indentation level. It is difficult to discern whether `B()` is a standalone call or part of `A()`'s execution tree.
* **Proposed Solution**:
  * Maintain a `ThreadLocal<Integer>` call depth counter in the agent runtime.
  * Increment depth at method entry, decrement at method exit.
  * Indent output based on call depth (e.g. two spaces per depth level or ASCII tree markers `└──`).
* **Acceptance Criteria**:
  * Nested calls appear indented under parent methods in stdout.
  * Call depth counter is thread-safe and properly decremented even on exceptions.
* **Implementation notes**: Delivered together with #6. Depth is `DurationTransformer.currentDepth()` (the size of the existing per-thread `LongStack`, so it is exception-safe by construction). It is stored on every record, shown as indentation in the `summary` call tree, and emitted as `depth` in `json`. Output is no longer printed live, so "nested calls appear indented" applies to the end-of-run report.

---

### 6. Structured & Configurable Output Format (JSON & Sinks)
* **Status**: Completed
* **Priority**: Medium (UX / Integration)
* **Problem**: Output was hardcoded to `System.out.println` in plain text (`Method.name() : 123`). That polluted stdout for applications relying on structured stdout logging, could not easily be ingested by log aggregators, and `System.out.println` synchronizes on `PrintStream`, which can cause thread contention.
* **Implemented Solution** (changed from the original "stream JSON lines" idea to buffer-and-report, for one-off debugging runs):
  * Slow calls are recorded into an in-memory buffer (`Recorder`, lock-free queue, capped at `duration.maxEvents`, default 100000; calls over the cap are only counted). Nothing is written on the hot path.
  * The report is written once from a JVM shutdown hook (it is not written if the JVM is killed with `kill -9`).
  * Configured with system properties:
    * `-Dduration.format=summary|json` (default `summary`)
    * `-Dduration.output=stdout|stderr|<file path>` (default `stdout`; a plain path, not a `file://` URL)
    * `-Dduration.maxEvents=<n>`
  * `summary`: top 10 methods by total time (calls, total, max, avg) followed by a call tree per thread, indented by call depth and capped at 200 lines per thread.
  * `json`: JSON lines `{"timestamp":…,"thread":"…","method":"…","durationMs":…,"depth":…}`, parents before children. If records were dropped at the buffer cap, the count is logged to `stderr`.
* **Acceptance Criteria**:
  * Users can select between `summary` and `json` output formats. *(met; the plain `text` format was replaced by `summary`)*
  * Output can be redirected to a dedicated log file. *(met)*
* **Not done**: a live/streaming mode and an HTML report. Records are lost if the JVM dies before the shutdown hook runs.

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

---

### 9. Fix Agent Loading & Classloader Isolation
* **Status**: Open
* **Priority**: Medium (Architecture / Reliability)
* **Problem**: 
  * The `DurationTransformer` constructor currently sets a static callback (`registerStartTimeFunction = registerFunction`), so instantiating a second transformer replaces the first one’s callback.
  * Instrumented code only works if its classloader can see the agent classes. If classes are loaded in isolated child classloaders (e.g. plugins, OSGi, application containers), attempting to invoke `DurationTransformer.pushStartTime()` results in `NoClassDefFoundError` or `ClassNotFoundException`.
* **Proposed Solution**:
  * Replace static callback assignment with an instance-safe registry or lookup mechanism.
  * Append agent runtime classes to the bootstrap classloader search path via `Instrumentation.appendToBootstrapClassLoaderSearch(jarFile)`.
* **Acceptance Criteria**:
  * Multiple transformer instances operate without clobbering each other's callbacks.
  * Classes in isolated child classloaders execute instrumented methods without `NoClassDefFoundError`.

---

### 10. Internal Agent Logging Facility & System Property Namespace Cleanup
* **Status**: Completed
* **Priority**: Medium (Usability / Robustness)
* **Problem**: 
  * Diagnostic and error output was scattered across the codebase using `System.out.println`, `System.err.println`, and raw `e.printStackTrace()`.
  * `DurationTransformer` unconditionally logged `Transforming: <className>` to `System.out` during classloading, polluting application output and interfering with stdout report redirection.
  * System properties used the generic `duration.*` prefix (`duration.format`, `duration.output`, `duration.maxEvents`), risking global collisions.
* **Implemented Solution**:
  * Introduced `AgentLogger`: a lightweight, zero-dependency diagnostic logger supporting log levels (`OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`).
  * Implemented monotonic elapsed time timestamping (`[+<ms>ms]`) using `System.nanoTime()` relative to agent initialization.
  * Transformation logs downgraded to `DEBUG` level (silent by default under `INFO`).
  * Diagnostic logs default to `System.err`, keeping them out of profiling reports written to `System.out` (e.g. JSON reports).
  * System properties structured into clean functional namespaces under `mvd.instrumentation.*`:
    * Core agent logic: `mvd.instrumentation.agent.threshold`, `mvd.instrumentation.agent.maxEvents`
    * Report configuration: `mvd.instrumentation.report.format`, `mvd.instrumentation.report.output`
    * Diagnostic logging: `mvd.instrumentation.log.level`, `mvd.instrumentation.log.output`
  * The old `duration.*` names are no longer read; the README has a migration table.
* **Acceptance Criteria**:
  * Bytecode transformation logging is suppressed by default (`INFO`) and enabled when `mvd.instrumentation.log.level=DEBUG`.
  * Profiling reports on `stdout` are not corrupted by internal agent diagnostic messages.
  * System properties are cleanly namespaced under `mvd.instrumentation.{agent,report,log}.*`.
  * Unit tests cover all log levels, timestamp formatting, exception formatting, and property configuration.

---

### 11. Agent Overhead Benchmarks & Performance Regression Gates
* **Status**: Open
* **Priority**: High (Performance)
* **Problem**:
  * The only benchmark ([`ClassFilterBenchmark.java`](src/test/java/mvd/instrumentation/ClassFilterBenchmark.java)) measures `ClassFilter.test`, which runs once per class load and is not on the critical path. The cost users actually pay runs on every call to every instrumented method: `pushStartTime()` (`ThreadLocal` lookup, `System.nanoTime()`, stack push), `popAndRegisterStartTime()`, and the fast rejection of calls under the threshold in `Recorder.record`. The injected `try/finally` also grows each instrumented method, which can stop the JIT from inlining it.
  * `make bench` runs JMH through `exec:java` with `@Fork(0)`. JMH warns that non-forked results are only good for debugging, and this setup cannot attach the agent (`-javaagent`) to the benchmark JVM. The figures quoted in item 3 come from these runs.
  * Nothing detects performance regressions: there is no allocation check, no limit on injected bytecode size, and no benchmark in CI.
* **Proposed Solution**:
  * **Deterministic gates** (stable, can fail the build on every PR):
    * Assert that the under-threshold path allocates 0 bytes per call, via JMH `-prof gc` (`gc.alloc.rate.norm`) or a JUnit test around `ThreadMXBean.getThreadAllocatedBytes()` (it counts bytes, not time, so it is not affected by machine load).
    * Cap the bytecode the transformer adds per method (instrumented minus original size), to protect JIT inlining.
  * **Overhead-per-call JMH benchmark**: the same small method called without the agent and with it, attached via `@Fork(jvmArgsAppend = "-javaagent:...")`, so the real javassist-generated code is measured. Cover the under-threshold path, the recorded path, and nested calls.
  * **Proper JMH runs**: `make bench` runs JMH's own `Main` with the test classpath (or a benchmark JAR) and forks by default (`@Fork(2)` or more); results can be written as JSON (`-rf json`). Benchmarks stay runnable on demand.
  * **CI timing check, master vs PR on the same runner**: one job builds both master and the PR and runs the same benchmarks back to back, failing only when the slowdown exceeds a set percentage (e.g. 10–15%) and is larger than JMH's error range. Comparing against stored numbers from other machines is avoided, because shared GitHub runners vary by ~5–10%. Runs on PRs touching `src/main` and nightly on master. Optionally publish trends with `benchmark-action/github-action-benchmark`; tighter thresholds would need a dedicated self-hosted runner.
* **Acceptance Criteria**:
  * A test fails if the under-threshold path of an instrumented call allocates.
  * A test fails if the bytecode injected per method grows beyond the agreed limit.
  * `make bench` runs forked JMH benchmarks that report per-call overhead with and without the agent.
  * A CI workflow compares PR and master benchmark results on the same runner and fails on a regression above the threshold.
  * `ClassFilterBenchmark` stays available on demand but does not gate CI.
