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

This compiles the agent, instruments the included demo service ([`DemoService`](https://github.com/mvd199/javaagent-instrumentation/blob/master/src/test/java/mvd/instrumentation/demo/DemoService.java)), and runs the showcase app ([`DemoApp`](https://github.com/mvd199/javaagent-instrumentation/blob/master/src/test/java/mvd/instrumentation/demo/DemoApp.java)).

### Demo Output
```text
==================================================
        DurationAgent Demonstration App           
==================================================
Threshold: 50 milliseconds

Transforming: mvd/instrumentation/demo/DemoService
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
      1        122       122       122   mvd.instrumentation.demo.DemoService.slowQuery()
      1         97        97        97   mvd.instrumentation.demo.DemoService.nestedWorkflow()
      1         85        85        85   mvd.instrumentation.demo.DemoService.heavyComputation()

--- Call tree: thread "main" --------------------------------------------
  [   0 ms]    122 ms  mvd.instrumentation.demo.DemoService.slowQuery()
  [ 124 ms]     97 ms  mvd.instrumentation.demo.DemoService.nestedWorkflow()
  [ 135 ms]     85 ms    mvd.instrumentation.demo.DemoService.heavyComputation()
```

To try the other report options, pass JVM options through `JAVA_OPTS`. Paths must be inside the project, which the container sees as `/workspace`:
```bash
make demo JAVA_OPTS="-Dduration.format=json"
make demo JAVA_OPTS="-Dduration.format=json -Dduration.output=/workspace/target/duration.jsonl"
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

* **Clean build artifacts**:
  ```bash
  make clean
  ```

*(If you have Maven and JDK installed locally, standard `mvn test` and `mvn package` also work).*

## Usage in Your Application

### 1. Configure the Whitelist ([`classes.cfg`](https://github.com/mvd199/javaagent-instrumentation/blob/master/classes.cfg))
List the fully qualified names of the classes you want to instrument, one per line. For example:

```text
com.mycompany.service.OrderService
com.mycompany.repository.UserRepository
```

The bundled `demo.cfg` (used by `make demo`) whitelists only `mvd.instrumentation.demo.DemoService`.

### 2. Attach the Agent to Your Application
Add the `-javaagent` flag to your JVM startup options:

```bash
java -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

### 3. Choose the Report (optional)
The report is buffered in memory and written once, when the JVM exits. Configure it with system properties:

| Property | Values | Default |
|---|---|---|
| `duration.format` | `summary` (tables and call tree), `json` (one JSON object per line) | `summary` |
| `duration.output` | `stdout`, `stderr` or a file path | `stdout` |
| `duration.maxEvents` | maximum number of recorded calls; slower calls beyond it are counted as dropped | `100000` |

```bash
java -Dduration.format=json -Dduration.output=/tmp/duration.jsonl \
     -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

Each JSON line looks like `{"timestamp":1696871234000,"thread":"main","method":"com.acme.Foo.bar()","durationMs":120,"depth":0}`. `depth` is the number of instrumented methods on the same thread that were still running when the call was made. The report is not written if the JVM is killed with `kill -9`.

## How to know which classes to include?
Use a debugger, APM traces, or logs to identify potential hotspot packages and classes to instrument.

## Roadmap & Planned Improvements
See [`ROADMAP.md`](https://github.com/mvd199/javaagent-instrumentation/blob/master/ROADMAP.md) for the full list of prioritized improvements, bug fixes, and architectural evolutions.