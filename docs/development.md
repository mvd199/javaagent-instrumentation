# Development

## Prerequisites

The [`Makefile`](../Makefile) runs Maven inside a Docker container (`maven:3.10.0-eclipse-temurin-11`, pinned so every run uses the same Maven; override with `make IMAGE=...`), so you don't need Java or Maven installed. Start Docker or Colima first:

```bash
colima start
```

If you have JDK 11+ and Maven installed locally, plain `mvn test` and `mvn package` work too. The code compiles to Java 8 bytecode (`<source>1.8</source>` in [`pom.xml`](../pom.xml)), so don't use language features or APIs newer than Java 8.

## Make targets

| Command | What it does |
|---|---|
| `make` or `make test` | Runs the unit tests, then prints a JaCoCo coverage summary |
| `make test ARGS="-Dtest=CoreRecorderTest"` | Runs a single test class |
| `make package` | Builds the shaded agent JAR at `target/java-instrumentation-1.0-SNAPSHOT.jar`, then runs `make verify-shading` on it |
| `make verify-shading` | Runs [`verify-shading.sh`](../scripts/verify-shading.sh) on the already built JAR: checks that javassist is relocated and that the agent works next to an application's own javassist |
| `make demo` | Builds the agent and runs [`DemoApp`](../src/test/java/mvd/demo/DemoApp.java) with it attached (pass `JAVA_OPTS="..."` for report options) |
| `make bench` | Runs the JMH microbenchmarks in [`CoreClassFilterBenchmark`](../src/test/java/mvd/instrumentation/CoreClassFilterBenchmark.java) |
| `make clean` | Deletes `target/` |

After renaming or deleting a class, run `make clean` before `make test`. Otherwise stale `.class` files of the old tests stay in `target/` and still run.

## Benchmarks

`make bench` is run by hand; it is not part of `make test` or CI. Run it whenever you touch code on a hot path (`CoreClassFilter.test`, `CoreCallTracker`, `CoreRecorder.record`) and compare the results with `master`. Automating this is [roadmap item #11](../ROADMAP.md).

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs `mvn -B verify` and `scripts/verify-shading.sh` on Java 11 for every pull request and every push to `master`, and uploads the JaCoCo report as a build artifact.

## Project layout

* `src/main/java/mvd/instrumentation/`: the agent. See [conventions](conventions.md) for how its classes are organized.
* `src/test/java/mvd/instrumentation/`: unit tests and benchmarks, plus `Sample*` fixture classes the transformer tests instrument.
* `src/test/java/mvd/demo/`: the demo app used by `make demo`. It lives outside the agent package because the agent never instruments its own package.
* `classes.cfg`, `demo.cfg`: example class whitelists.
