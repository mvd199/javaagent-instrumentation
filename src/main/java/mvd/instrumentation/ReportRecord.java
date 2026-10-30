package mvd.instrumentation;

/** A single instrumented invocation that exceeded the duration threshold. */
final class ReportRecord {
    final long startNanos;
    final long timestampMillis;
    final String thread;
    final String method;
    final long durationMs;
    /** Number of instrumented frames above this call on the same thread; 0 for an outermost call. */
    final int depth;

    ReportRecord(long startNanos, long timestampMillis, String thread, String method, long durationMs, int depth) {
        this.startNanos = startNanos;
        this.timestampMillis = timestampMillis;
        this.thread = thread;
        this.method = method;
        this.durationMs = durationMs;
        this.depth = depth;
    }

    long endNanos() {
        return startNanos + durationMs * 1_000_000L;
    }
}
