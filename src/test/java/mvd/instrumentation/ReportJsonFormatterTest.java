package mvd.instrumentation;

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReportJsonFormatterTest {
    private static String render(Report report) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        new ReportJsonFormatter().write(report, out);
        out.flush();
        return text.toString();
    }

    @Test
    public void writesOneObjectPerLineParentsFirst() {
        List<ReportRecord> records = Arrays.asList(
                new ReportRecord(2_000_000L, 1696871234100L, "main", "a.B.child()", 90, 1),
                new ReportRecord(1_000_000L, 1696871234000L, "main", "a.B.parent()", 120, 0));

        String[] lines = render(new Report(records, 0, 50)).split("\\R");

        assertEquals(2, lines.length);
        assertEquals("{\"timestamp\":1696871234000,\"thread\":\"main\",\"method\":\"a.B.parent()\",\"durationMs\":120,\"depth\":0}", lines[0]);
        assertEquals("{\"timestamp\":1696871234100,\"thread\":\"main\",\"method\":\"a.B.child()\",\"durationMs\":90,\"depth\":1}", lines[1]);
    }

    @Test
    public void escapesSpecialCharacters() {
        List<ReportRecord> records = Arrays.asList(
                new ReportRecord(1L, 1L, "t\"1\\", "a.B.m(java.lang.String[])\n\u0001", 60, 0));

        String line = render(new Report(records, 0, 50)).trim();

        assertEquals("{\"timestamp\":1,\"thread\":\"t\\\"1\\\\\",\"method\":\"a.B.m(java.lang.String[])\\n\\u0001\",\"durationMs\":60,\"depth\":0}", line);
    }

    @Test
    public void doesNotIncludeDroppedCountInJsonOutput() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "main", "a.B.m()", 60, 0));

        String[] output = render(new Report(records, 7, 50)).split("\\R");

        assertEquals(1, output.length);
        assertEquals("{\"timestamp\":1,\"thread\":\"main\",\"method\":\"a.B.m()\",\"durationMs\":60,\"depth\":0}", output[0]);
    }

    @Test
    public void linesEndWithNewlineRegardlessOfPlatform() {
        List<ReportRecord> records = Arrays.asList(
                new ReportRecord(1L, 1L, "main", "a.B.m1()", 60, 0),
                new ReportRecord(2L, 2L, "main", "a.B.m2()", 70, 0));

        String text = render(new Report(records, 0, 50));

        assertEquals(2, text.split("\n", -1).length - 1);
        assertEquals(false, text.contains("\r"));
    }

    @Test
    public void emptyReportProducesNoOutput() {
        assertEquals("", render(new Report(Arrays.asList(), 0, 50)));
    }
}
