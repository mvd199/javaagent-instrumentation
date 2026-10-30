package mvd.instrumentation;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffers calls slower than the threshold in memory until the report is written.
 * Holds at most {@code maxEvents} records; further slow calls are only counted.
 */
final class CoreRecorder {
    private final long thresholdMs;
    private final int maxEvents;
    private final Queue<ReportRecord> records = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalSlowCalls = new AtomicLong();

    CoreRecorder(long thresholdMs, int maxEvents) {
        this.thresholdMs = thresholdMs;
        this.maxEvents = maxEvents;
    }

    /** Matches {@link CoreCallback}, so it can be installed directly with {@code recorder::record}. */
    void record(String method, long startNanos, int depth) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (durationMs <= thresholdMs) {
            return;
        }
        if (totalSlowCalls.incrementAndGet() > maxEvents) {
            return;
        }
        records.add(new ReportRecord(startNanos, System.currentTimeMillis() - durationMs,
                Thread.currentThread().getName(), method, durationMs, depth));
    }

    Report snapshot() {
        long total = totalSlowCalls.get();
        long dropped = Math.max(0L, total - maxEvents);
        return new Report(records, dropped, thresholdMs);
    }
}
