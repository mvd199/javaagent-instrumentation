package mvd.instrumentation;

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SummaryReporterTest {
    private static final long MS = 1_000_000L;

    private static CallRecord call(String thread, String method, long startMs, long durationMs, int depth) {
        return new CallRecord(startMs * MS, 1_000_000L + startMs, thread, method, durationMs, depth);
    }

    private static String render(Report report) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        new SummaryReporter().write(report, out);
        out.flush();
        return text.toString();
    }

    private static List<String> lines(String text) {
        return Arrays.asList(text.split("\\R"));
    }

    @Test
    public void reportsWhenNothingExceededTheThreshold() {
        String text = render(new Report(new ArrayList<>(), 0, 50));

        assertTrue(text, text.contains("recorded: 0 calls"));
        assertTrue(text, text.contains("No calls exceeded 50 ms."));
        assertFalse(text, text.contains("Slowest methods"));
    }

    @Test
    public void headerShowsCountsThreadsAndWallTime() {
        List<CallRecord> records = Arrays.asList(
                call("main", "a.B.run()", 0, 1000, 0),
                call("worker", "a.B.job()", 500, 1500, 0));

        String text = render(new Report(records, 4, 50));

        assertTrue(text, text.contains("threshold: 50 ms   recorded: 2 calls   dropped: 4   threads: 2   wall: 2.00 s"));
    }

    @Test
    public void aggregatesPerMethodAndRanksByTotalTime() {
        List<CallRecord> records = Arrays.asList(
                call("main", "a.B.many()", 0, 100, 0),
                call("main", "a.B.many()", 200, 300, 0),
                call("main", "a.B.once()", 600, 350, 0));

        List<String> lines = lines(render(new Report(records, 0, 50)));

        int header = indexOfLineContaining(lines, "calls   total ms");
        // many(): 2 calls, total 400, max 300, avg 200; ranks above once() (350)
        assertEquals("      2        400       300       200   a.B.many()", lines.get(header + 1));
        assertEquals("      1        350       350       350   a.B.once()", lines.get(header + 2));
    }

    @Test
    public void limitsTheMethodTableToTopTen() {
        List<CallRecord> records = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            records.add(call("main", "a.B.m" + i + "()", i * 10, 100 + i, 0));
        }

        String text = render(new Report(records, 0, 50));

        assertTrue(text, text.contains("a.B.m11()"));
        assertTrue(text, text.contains("a.B.m2()"));
        // m0 and m1 are the two smallest totals; they only appear in the tree, never in the table
        List<String> lines = lines(text);
        int header = indexOfLineContaining(lines, "calls   total ms");
        int treeStart = indexOfLineContaining(lines, "Call tree");
        for (int i = header; i < treeStart; i++) {
            assertFalse(lines.get(i), lines.get(i).endsWith("a.B.m0()") || lines.get(i).endsWith("a.B.m1()"));
        }
    }

    @Test
    public void callTreeIndentsChildrenUnderTheirParentsInStartOrder() {
        // Records are given in completion order, inner call first.
        List<CallRecord> records = Arrays.asList(
                call("main", "a.B.child()", 8, 310, 1),
                call("main", "a.B.parent()", 5, 640, 0),
                call("main", "a.B.second()", 700, 210, 0));

        List<String> lines = lines(render(new Report(records, 0, 50)));

        int tree = indexOfLineContaining(lines, "Call tree: thread \"main\"");
        assertEquals("  [   0 ms]    640 ms  a.B.parent()", lines.get(tree + 1));
        assertEquals("  [   3 ms]    310 ms    a.B.child()", lines.get(tree + 2));
        assertEquals("  [ 695 ms]    210 ms  a.B.second()", lines.get(tree + 3));
    }

    @Test
    public void callTreeHasOneSectionPerThreadWithOffsetsFromTheFirstCall() {
        List<CallRecord> records = Arrays.asList(
                call("main", "a.B.run()", 0, 1200, 0),
                call("worker-1", "a.B.job()", 40, 60, 0));

        String text = render(new Report(records, 0, 50));

        assertTrue(text, text.contains("Call tree: thread \"main\""));
        assertTrue(text, text.contains("Call tree: thread \"worker-1\""));
        assertTrue(text, text.contains("[  40 ms]     60 ms  a.B.job()"));
    }

    @Test
    public void callTreeIsCappedPerThreadWithAMoreLine() {
        List<CallRecord> records = new ArrayList<>();
        int total = SummaryReporter.MAX_TREE_LINES_PER_THREAD + 5;
        for (int i = 0; i < total; i++) {
            records.add(call("main", "a.B.m()", i, 60, 0));
        }

        List<String> lines = lines(render(new Report(records, 0, 50)));

        long treeLines = lines.stream().filter(l -> l.startsWith("  [")).count();
        assertEquals(SummaryReporter.MAX_TREE_LINES_PER_THREAD, treeLines);
        assertTrue(lines.contains("  ... 5 more"));
    }

    @Test
    public void alignsOffsetsBeyondFourDigits() {
        List<CallRecord> records = Arrays.asList(
                call("main", "a.B.early()", 0, 100, 0),
                call("main", "a.B.late()", 12345, 100, 0));

        List<String> lines = lines(render(new Report(records, 0, 50)));

        int tree = indexOfLineContaining(lines, "Call tree: thread \"main\"");
        assertEquals("  [    0 ms]    100 ms  a.B.early()", lines.get(tree + 1));
        assertEquals("  [12345 ms]    100 ms  a.B.late()", lines.get(tree + 2));
    }

    private static int indexOfLineContaining(List<String> lines, String fragment) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(fragment)) {
                return i;
            }
        }
        throw new AssertionError("No line contains '" + fragment + "' in:\n" + String.join("\n", lines));
    }
}
