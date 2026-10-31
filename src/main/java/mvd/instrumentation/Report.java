package mvd.instrumentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable snapshot of the slow calls recorded during the run. */
final class Report {
    /** Parents sort before their children: by start time, then by depth. */
    static final Comparator<ReportRecord> CALL_ORDER =
            Comparator.<ReportRecord>comparingLong(r -> r.startNanos).thenComparingInt(r -> r.depth);

    final List<ReportRecord> records;
    final long dropped;
    final long thresholdMs;

    /** Copies {@code records}, so the report stays immutable whatever the caller does with its list. */
    Report(Collection<ReportRecord> records, long dropped, long thresholdMs) {
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
        this.dropped = dropped;
        this.thresholdMs = thresholdMs;
    }

    List<ReportRecord> inCallOrder() {
        List<ReportRecord> sorted = new ArrayList<>(records);
        sorted.sort(CALL_ORDER);
        return sorted;
    }
}
