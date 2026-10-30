package mvd.instrumentation;

/** Receives every finished instrumented call. Primitive parameters keep the per-call path free of boxing. */
@FunctionalInterface
interface CoreCallback {
    void onCallFinished(String method, long startNanos, int depth);
}
