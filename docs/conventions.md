# Conventions

## One package, prefixed class names

All agent classes live in the single package `mvd.instrumentation` and are package-private unless something outside the package has to call them. That keeps the agent's internals closed: the compiler stops anything outside the package from depending on them.

Instead of subpackages, a class name prefix shows which part of the agent a class belongs to:

| Prefix | Part | Classes |
|---|---|---|
| `Agent*`, `DurationAgent` | Entry point, configuration and shared utilities | `DurationAgent`, `AgentConfig`, `AgentLogger` |
| `Core*` | Instrumentation and recording: everything that runs while the application runs | `CoreTransformer`, `CoreClassFilter`, `CoreCallTracker`, `CoreCallback`, `CoreRecorder` |
| `Report*` | The report written at shutdown: its data, formats and output | `Report`, `ReportRecord`, `ReportFormat`, `ReportOutput`, `ReportFormatter`, `ReportJsonFormatter`, `ReportSummaryFormatter`, `ReportWriter` |

Rules:
* A new class takes the prefix of the part it belongs to. Tests follow the class they test (`CoreRecorderTest`).
* `Report*` classes don't use `Core*` classes. Data flows one way: `Core*` produces records, `Report*` renders them.
* Only two classes are public, because code outside the package must reach them:
  * `DurationAgent`, whose `premain` the JVM calls.
  * `CoreCallTracker`, whose `enter()` and `exit()` the injected bytecode calls from application classes.

## Configuration is data

`AgentConfig` only reads system properties and validates them, so an instance is always well formed. It never creates components. `DurationAgent.premain` builds the components (recorder, writer, transformer) from the config.

Reading and validation are separate steps: `readString` and `readLong` only read and apply defaults, and `requireInRange` only validates. Invalid values throw an `IllegalArgumentException` at startup, with messages in the form `<property> must be <rule>: <value>`.

## Hot path

`CoreCallTracker.enter()` and `exit()`, and `CoreRecorder.record`, run on every call to every instrumented method. Code on that path:
* must not allocate for calls below the threshold. Pass primitives, not objects (that's why `CoreCallback` takes `method, startNanos, depth` instead of a record).
* must never throw into the application. `exit()` swallows anything the callback throws.

`CoreClassFilter.test` runs once per loaded class and must not allocate either. Check changes to any of these with `make bench`.

## Logging

Diagnostics go through `AgentLogger.LOG`, which always writes to `stderr` so it never mixes with a report on `stdout`. Build expensive debug messages lazily: `LOG.debug(() -> "..." + value)`.

## Style

* Match the surrounding code: 4-space indentation, lines up to about 120 characters, no IDE auto-formatting.
* Keep formatting-only changes out of commits that change behavior.
* Tests that check log output capture `System.err` with `System.setErr` and restore it in a `finally` block.
