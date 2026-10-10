# javaagent-instrumentation

A lightweight Java agent that instruments methods in whitelisted classes to detect performance bottlenecks. It logs to stdout any method invocation that takes longer than 50 milliseconds to execute.

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
         -> Completed! (No log produced, under 50ms threshold)

[Step 2] Calling slowQuery() (~120ms)...
mvd.instrumentation.demo.DemoService.slowQuery() : 121
         -> Completed!

[Step 3] Calling nestedWorkflow() (contains heavyComputation ~80ms)...
mvd.instrumentation.demo.DemoService.heavyComputation() : 82
mvd.instrumentation.demo.DemoService.nestedWorkflow() : 94
         -> Completed!

==================================================
             Demo Finished Successfully           
==================================================
```

Notice:
1. `fastTask()` (< 50ms) completes silently without being logged.
2. `slowQuery()` (~120ms) is logged with its duration.
3. In `nestedWorkflow()`, both the inner slow computation and the outer workflow are reported.

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
mvd.instrumentation.demo.DemoService
com.mycompany.service.OrderService
com.mycompany.repository.UserRepository
```

### 2. Attach the Agent to Your Application
Add the `-javaagent` flag to your JVM startup options:

```bash
java -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

## How to know which classes to include?
Use a debugger, APM traces, or logs to identify potential hotspot packages and classes to instrument.

## Roadmap & Planned Improvements
See [`ROADMAP.md`](https://github.com/mvd199/javaagent-instrumentation/blob/master/ROADMAP.md) for the full list of prioritized improvements, bug fixes, and architectural evolutions.