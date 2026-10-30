package mvd.instrumentation;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * Buffers calls slower than the threshold in memory until the report is written.
 * Holds at most {@code maxEvents} records; further slow calls are only counted.
 */
final class CoreRecorder {
    private final long thresholdMs;
    private final int maxEvents;
    private final IntSupplier depthSource;
    private final Queue<ReportRecord> records = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalSlowCalls = new AtomicLong();

    /** {@code depthSource} is only consulted for calls that pass the threshold, keeping fast calls cheap. */
    CoreRecorder(long thresholdMs, int maxEvents, IntSupplier depthSource) {
        this.thresholdMs = thresholdMs;
        this.maxEvents = maxEvents;
        this.depthSource = depthSource;
    }

    void record(String method, long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (durationMs <= thresholdMs) {
            return;
        }
        if (totalSlowCalls.incrementAndGet() > maxEvents) {
            return;
        }
        records.add(new ReportRecord(startNanos, System.currentTimeMillis() - durationMs,
                Thread.currentThread().getName(), method, durationMs, depthSource.getAsInt()));
    }

    Report snapshot() {
        long total = totalSlowCalls.get();
        long dropped = Math.max(0L, total - maxEvents);
        return new Report(new ArrayList<>(records), dropped, thresholdMs);
    }
}
