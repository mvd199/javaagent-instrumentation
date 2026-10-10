package mvd.instrumentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable snapshot of everything the {@link Recorder} captured. */
final class Report {
    /** Parents sort before their children: by start time, then by depth. */
    static final Comparator<CallRecord> CALL_ORDER =
            Comparator.<CallRecord>comparingLong(r -> r.startNanos).thenComparingInt(r -> r.depth);

    final List<CallRecord> records;
    final long dropped;
    final long thresholdMs;

    Report(List<CallRecord> records, long dropped, long thresholdMs) {
        this.records = records;
        this.dropped = dropped;
        this.thresholdMs = thresholdMs;
    }

    List<CallRecord> inCallOrder() {
        List<CallRecord> sorted = new ArrayList<>(records);
        sorted.sort(CALL_ORDER);
        return sorted;
    }
}
