package mvd.instrumentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable snapshot of everything the {@link CoreRecorder} captured. */
final class Report {
    /** Parents sort before their children: by start time, then by depth. */
    static final Comparator<ReportRecord> CALL_ORDER =
            Comparator.<ReportRecord>comparingLong(r -> r.startNanos).thenComparingInt(r -> r.depth);

    final List<ReportRecord> records;
    final long dropped;
    final long thresholdMs;

    Report(List<ReportRecord> records, long dropped, long thresholdMs) {
        this.records = records;
        this.dropped = dropped;
        this.thresholdMs = thresholdMs;
    }

    List<ReportRecord> inCallOrder() {
        List<ReportRecord> sorted = new ArrayList<>(records);
        sorted.sort(CALL_ORDER);
        return sorted;
    }
}
