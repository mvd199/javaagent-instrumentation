# javaagent-instrumentation

A lightweight Java agent that instruments methods in whitelisted classes to detect performance bottlenecks. It records every method invocation that takes longer than 50 milliseconds and prints a report when the JVM exits.

## Quick Start (Demo)

A [`Makefile`](https://github.com/mvd199/javaagent-instrumentation/blob/master/Makefile) is provided to run builds, tests, and the demo inside a Docker / Colima container without requiring Maven or Java installed locally on the host.

Ensure Colima (or Docker) is running:
```bash
colima start
```

Run the showcase demo with a single command:
```bash
make demo
```

This compiles the agent, instruments the included demo service ([`DemoService`](https://github.com/mvd199/javaagent-instrumentation/blob/master/src/test/java/mvd/demo/DemoService.java)), and runs the showcase app ([`DemoApp`](https://github.com/mvd199/javaagent-instrumentation/blob/master/src/test/java/mvd/demo/DemoApp.java)).

### Demo Output
```text
==================================================
        DurationAgent Demonstration App           
==================================================
Threshold: 50 milliseconds

[Step 1] Calling fastTask() (~10ms)...
         -> Completed! (Under the 50ms threshold, so it is not recorded)

[Step 2] Calling slowQuery() (~120ms)...
         -> Completed!

[Step 3] Calling nestedWorkflow() (contains heavyComputation ~80ms)...
         -> Completed!

==================================================
             Demo Finished Successfully           
==================================================
=== Duration report ===================================================
threshold: 50 ms   recorded: 3 calls   dropped: 0   threads: 1   wall: 0.22 s

--- Slowest methods (top 10, by total time) -----------------------------
  calls   total ms    max ms    avg ms   method
      1        122       122       122   mvd.demo.DemoService.slowQuery()
      1         97        97        97   mvd.demo.DemoService.nestedWorkflow()
      1         85        85        85   mvd.demo.DemoService.heavyComputation()

--- Call tree: thread "main" --------------------------------------------
  [   0 ms]    122 ms  mvd.demo.DemoService.slowQuery()
  [ 124 ms]     97 ms  mvd.demo.DemoService.nestedWorkflow()
  [ 135 ms]     85 ms    mvd.demo.DemoService.heavyComputation()
```

To try the other report options, pass JVM options through `JAVA_OPTS`. Paths must be inside the project, which the container sees as `/workspace`:
```bash
make demo JAVA_OPTS="-Dmvd.instrumentation.report.format=json"
make demo JAVA_OPTS="-Dmvd.instrumentation.report.format=json -Dmvd.instrumentation.report.output=/workspace/target/duration.jsonl"
```

Notice:
1. `fastTask()` (< 50ms) is never recorded.
2. `slowQuery()` (~120ms) is recorded with its duration.
3. In `nestedWorkflow()`, both the inner slow computation and the outer workflow are reported, and `heavyComputation()` is indented under its caller. The `[ n ms]` column is the start offset from the first recorded call.

## Building and Testing

* **Run unit tests** (default):
  ```bash
  make
  # or
  make test
  ```

* **Run a single test**:
  ```bash
  make test ARGS="-Dtest=DurationAgentTest"
  ```

* **Build the shaded agent JAR**:
  ```bash
  make package
  ```
  The packaged JAR will be created at: `target/java-instrumentation-1.0-SNAPSHOT.jar`

* **Run microbenchmarks (JMH)**:
  ```bash
  make bench
  ```

* **Clean build artifacts**:
  ```bash
  make clean
  ```

*(If you have Maven and JDK installed locally, standard `mvn test` and `mvn package` also work).*

## Usage in Your Application

### 1. Configure the Whitelist ([`classes.cfg`](https://github.com/mvd199/javaagent-instrumentation/blob/master/classes.cfg))
List the classes or package patterns you want to instrument, one per line. Blank lines, leading/trailing whitespace, and `#` comments are ignored.

Supports:
- **Exact class**: `com.mycompany.service.OrderService`
- **Single-level wildcard**: `com.mycompany.service.*` (matches direct classes in package)
- **Recursive wildcard**: `com.mycompany.repository.**` (matches package and all subpackages)

JDK classes (`java.*`, `javax.*`, `jdk.*`, `sun.*`, `com.sun.*`), javassist and the agent's own classes are never instrumented, because instrumenting them can abort JVM startup or stop the report from being written. A pattern that covers them logs a warning, and a bare `**` is rejected at startup.

```text
# Exact service class
com.mycompany.service.OrderService

# All controllers directly in the web package
com.mycompany.web.*

# All repository classes and subpackages
com.mycompany.repository.**
```

The bundled `demo.cfg` (used by `make demo`) whitelists only `mvd.demo.DemoService`.

### 2. Attach the Agent to Your Application
Add the `-javaagent` flag to your JVM startup options:

```bash
java -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

### 3. Configure Agent, Report & Logging (optional)
The report is buffered in memory and written once, when the JVM exits. Configure it with system properties prefixed by `mvd.instrumentation.`:

| Category | Property | Values | Default | Description |
|---|---|---|---|---|
| **Agent** | `mvd.instrumentation.agent.threshold` | number (ms) | `50` | Calls that take longer than this are recorded; values above `180000` (3 minutes) are capped with a warning, so a too-high value cannot silently disable recording |
| **Agent** | `mvd.instrumentation.agent.maxEvents` | number | `100000` | Maximum number of recorded calls; slower calls beyond it are counted as dropped |
| **Report** | `mvd.instrumentation.report.format` | `summary`, `json` | `summary` | Report output format (`summary` table and call tree, or JSON lines) |
| **Report** | `mvd.instrumentation.report.output` | `stdout`, `stderr`, file path | `stdout` | Profiling report destination |
| **Logging** | `mvd.instrumentation.log.level` | `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG` | `INFO` | Internal agent diagnostic log level |

```bash
java -Dmvd.instrumentation.agent.threshold=100 \
     -Dmvd.instrumentation.report.format=json \
     -Dmvd.instrumentation.report.output=/tmp/duration.jsonl \
     -Dmvd.instrumentation.log.level=INFO \
     -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

- **JSON lines:** each line looks like `{"timestamp":1696871234000,"thread":"main","method":"com.acme.Foo.bar()","durationMs":120,"depth":0}`. `depth` is how many instrumented calls were still running on the same thread.
- **Dropped calls:** calls beyond `mvd.instrumentation.agent.maxEvents` are counted, and the count appears in the `summary` header (or as a warning on `stderr` for `json`).
- **Invalid values** (unknown format, non-numeric or negative number) stop the JVM at startup with an error naming the property.
- **Diagnostic logs** (e.g. `[+12ms] [DEBUG] Transforming: ...`) always go to `stderr`, so they never mix with the report.
- **No report on `kill -9`:** it is written by a shutdown hook.

#### Upgrading from `duration.*` properties
All properties moved under the `mvd.instrumentation.` prefix. The old names are no longer read: if you still pass them, they are ignored and the defaults apply, so update your startup flags.

| Old property | New property |
|---|---|
| `duration.format` | `mvd.instrumentation.report.format` |
| `duration.output` | `mvd.instrumentation.report.output` |
| `duration.maxEvents` | `mvd.instrumentation.agent.maxEvents` |
| *(hardcoded 50 ms)* | `mvd.instrumentation.agent.threshold` |

The `Transforming: <class>` lines the agent used to print to `stdout` are now `DEBUG` logs on `stderr`; set `-Dmvd.instrumentation.log.level=DEBUG` to see them.

## How to know which classes to include?
Use a debugger, APM traces, or logs to identify potential hotspot packages and classes to instrument.

## Roadmap & Planned Improvements
See [`ROADMAP.md`](https://github.com/mvd199/javaagent-instrumentation/blob/master/ROADMAP.md) for the full list of prioritized improvements, bug fixes, and architectural evolutions.

## License
Copyright 2026 MikeD. Licensed under the [Apache License, Version 2.0](LICENSE).
