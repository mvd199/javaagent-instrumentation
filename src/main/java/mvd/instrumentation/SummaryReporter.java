package mvd.instrumentation;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Human-readable report: top methods by total time, then a call tree per thread. */
final class SummaryReporter implements Reporter {
    static final int TOP_METHODS = 10;
    static final int MAX_TREE_LINES_PER_THREAD = 200;

    @Override
    public void write(Report report, PrintWriter out) {
        List<CallRecord> calls = report.inCallOrder();
        writeHeader(report, calls, out);
        if (calls.isEmpty()) {
            out.println();
            out.println("No calls exceeded " + report.thresholdMs + " ms.");
            return;
        }
        writeTopMethods(calls, out);
        writeCallTrees(calls, out);
    }

    private static void writeHeader(Report report, List<CallRecord> calls, PrintWriter out) {
        Set<String> threads = new HashSet<>();
        long firstStart = Long.MAX_VALUE;
        long lastEnd = Long.MIN_VALUE;
        for (CallRecord call : calls) {
            threads.add(call.thread);
            firstStart = Math.min(firstStart, call.startNanos);
            lastEnd = Math.max(lastEnd, call.endNanos());
        }
        double wallSeconds = calls.isEmpty() ? 0 : (lastEnd - firstStart) / 1e9;
        out.println("=== Duration report ===================================================");
        out.println(String.format(Locale.ROOT, "threshold: %d ms   recorded: %d calls   dropped: %d   threads: %d   wall: %.2f s",
                report.thresholdMs, calls.size(), report.dropped, threads.size(), wallSeconds));
    }

    private static void writeTopMethods(List<CallRecord> calls, PrintWriter out) {
        Map<String, long[]> stats = new LinkedHashMap<>(); // {count, total, max}
        for (CallRecord call : calls) {
            long[] s = stats.computeIfAbsent(call.method, k -> new long[3]);
            s[0]++;
            s[1] += call.durationMs;
            s[2] = Math.max(s[2], call.durationMs);
        }
        List<Map.Entry<String, long[]>> ranked = new ArrayList<>(stats.entrySet());
        Collections.sort(ranked, (a, b) -> {
            int byTotal = Long.compare(b.getValue()[1], a.getValue()[1]);
            return byTotal != 0 ? byTotal : a.getKey().compareTo(b.getKey());
        });

        out.println();
        out.println("--- Slowest methods (top " + TOP_METHODS + ", by total time) -----------------------------");
        out.println(String.format(Locale.ROOT, "%7s %10s %9s %9s   %s", "calls", "total ms", "max ms", "avg ms", "method"));
        for (Map.Entry<String, long[]> entry : ranked.subList(0, Math.min(TOP_METHODS, ranked.size()))) {
            long[] s = entry.getValue();
            out.println(String.format(Locale.ROOT, "%7d %10d %9d %9d   %s", s[0], s[1], s[2], s[1] / s[0], entry.getKey()));
        }
    }

    private static void writeCallTrees(List<CallRecord> calls, PrintWriter out) {
        long origin = calls.get(0).startNanos;
        Map<String, List<CallRecord>> byThread = new LinkedHashMap<>();
        for (CallRecord call : calls) {
            byThread.computeIfAbsent(call.thread, k -> new ArrayList<>()).add(call);
        }
        for (Map.Entry<String, List<CallRecord>> thread : byThread.entrySet()) {
            out.println();
            out.println("--- Call tree: thread \"" + thread.getKey() + "\" --------------------------------------------");
            List<CallRecord> threadCalls = thread.getValue();
            for (CallRecord call : threadCalls.subList(0, Math.min(MAX_TREE_LINES_PER_THREAD, threadCalls.size()))) {
                out.println(String.format(Locale.ROOT, "  [%4d ms] %6d ms  %s%s",
                        (call.startNanos - origin) / 1_000_000L, call.durationMs, indent(call.depth), call.method));
            }
            if (threadCalls.size() > MAX_TREE_LINES_PER_THREAD) {
                out.println("  ... " + (threadCalls.size() - MAX_TREE_LINES_PER_THREAD) + " more");
            }
        }
    }

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }
}
