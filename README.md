# javaagent-instrumentation

A lightweight Java agent that instruments methods in whitelisted classes to detect performance bottlenecks. It records every method invocation that takes longer than 50 milliseconds and prints a report when the JVM exits.

## Quick Start (Demo)

A [`Makefile`](Makefile) is provided to run builds, tests, and the demo inside a Docker / Colima container without requiring Maven or Java installed locally on the host.

Ensure Colima (or Docker) is running:
```bash
colima start
```

Run the showcase demo with a single command:
```bash
make demo
```

This compiles the agent, instruments the included demo service ([`DemoService`](src/test/java/mvd/demo/DemoService.java)), and runs the showcase app ([`DemoApp`](src/test/java/mvd/demo/DemoApp.java)).

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

## Documentation

* [User guide](docs/user-guide.md): configure the class whitelist, attach the agent, and set report and logging options.
* [Development](docs/development.md): build, test, benchmark and CI.
* [Conventions](docs/conventions.md): how the code is organized and the rules contributors follow.
* [Roadmap](ROADMAP.md): planned improvements and the history of completed ones.

## License
Copyright 2026 MikeD. Licensed under the [Apache License, Version 2.0](LICENSE).
