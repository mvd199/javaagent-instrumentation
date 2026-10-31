# User guide

How to attach the agent to your application and configure it. To try it first, see the [quick start](../README.md#quick-start-demo).

## 1. Configure the Whitelist ([`classes.cfg`](../classes.cfg))
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

## 2. Attach the Agent to Your Application
Add the `-javaagent` flag to your JVM startup options:

```bash
java -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

## 3. Configure Agent, Report & Logging (optional)
The report is buffered in memory and written once, when the JVM exits. Configure it with system properties prefixed by `mvd.instrumentation.`:

| Category | Property | Values | Default | Description |
|---|---|---|---|---|
| **Agent** | `mvd.instrumentation.agent.threshold` | number (ms) | `50` | Calls that take longer than this are recorded. Must be between `0` and `180000` (3 minutes), so a too-high value cannot silently disable recording |
| **Agent** | `mvd.instrumentation.agent.maxEvents` | number | `100000` | Maximum number of recorded calls; slower calls beyond it are counted as dropped |
| **Report** | `mvd.instrumentation.report.format` | `text`, `json`, `html` | `text` | Report output format (`text`: slowest-methods table and call tree; `json`: JSON lines; `html`: one self-contained interactive page) |
| **Report** | `mvd.instrumentation.report.output` | `stdout`, `stderr`, file path | `stdout` | Profiling report destination |
| **Logging** | `mvd.instrumentation.log.level` | `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG` | `INFO` | Internal agent diagnostic log level |

```bash
java -Dmvd.instrumentation.agent.threshold=100 \
     -Dmvd.instrumentation.report.format=json \
     -Dmvd.instrumentation.report.output=/tmp/duration.jsonl \
     -Dmvd.instrumentation.log.level=INFO \
     -javaagent:target/java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg -jar your-app.jar
```

- **JSON lines:** the first line is `{"type":"meta","thresholdMs":50,"dropped":0}`, followed by one line per call, parents first: `{"type":"call","timestamp":1696871234000,"thread":"main","method":"com.acme.Foo.bar()","durationMs":120,"depth":0}`. `depth` is how many instrumented calls were still running on the same thread. The `html` report embeds exactly these lines.
- **HTML:** use `report.format=html` with a `report.output` file such as `/tmp/duration.html` and open it in a browser; it works offline. The page filters by method and thread and shows a collapsible call tree.
- **Dropped calls:** calls beyond `mvd.instrumentation.agent.maxEvents` are counted, and the count appears in the `text` header, the `meta` line of `json`, and as a stat in `html`.
- **Invalid values** (unknown format, non-numeric or out-of-range number) stop the JVM at startup with an error naming the property.
- **Diagnostic logs** (e.g. `[+12ms] [DEBUG] Transforming: ...`) always go to `stderr`, so they never mix with the report.
- **No report on `kill -9`:** it is written by a shutdown hook.

## Upgrading from `duration.*` properties
All properties moved under the `mvd.instrumentation.` prefix. The old names are no longer read: if you still pass them, they are ignored and the defaults apply, so update your startup flags.

| Old property | New property |
|---|---|
| `duration.format` | `mvd.instrumentation.report.format` (the `summary` value is now `text`) |
| `duration.output` | `mvd.instrumentation.report.output` |
| `duration.maxEvents` | `mvd.instrumentation.agent.maxEvents` |
| *(hardcoded 50 ms)* | `mvd.instrumentation.agent.threshold` |

Two later changes also need updated flags. Both now stop the JVM at startup with an error instead of being accepted:
* `mvd.instrumentation.report.format=summary` was renamed to `text`.
* A `mvd.instrumentation.agent.threshold` above `180000` used to be capped with a warning.

The `Transforming: <class>` lines the agent used to print to `stdout` are now `DEBUG` logs on `stderr`; set `-Dmvd.instrumentation.log.level=DEBUG` to see them.

## How do I know which classes to include?
Use a debugger, APM traces, or logs to identify potential hotspot packages and classes to instrument.
