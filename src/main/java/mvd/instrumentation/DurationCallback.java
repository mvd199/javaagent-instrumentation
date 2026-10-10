package mvd.instrumentation;

/** Receives every finished instrumented call. Primitive parameters keep the per-call path free of boxing. */
@FunctionalInterface
interface DurationCallback {
    void onCallFinished(String method, long startNanos);
}
