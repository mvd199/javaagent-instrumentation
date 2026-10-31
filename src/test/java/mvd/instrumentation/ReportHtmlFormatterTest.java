package mvd.instrumentation;

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReportHtmlFormatterTest {
    private static String render(Report report) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        new ReportHtmlFormatter().write(report, out);
        out.flush();
        return text.toString();
    }

    @Test
    public void embedsDataScriptAndStylesheet() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1000L, "main", "a.B.m()", 60, 0));

        String html = render(new Report(records, 3, 50));

        assertTrue(html, html.contains("{\"type\":\"meta\",\"thresholdMs\":50,\"dropped\":3}\n"
                + "{\"type\":\"call\",\"timestamp\":1000,\"thread\":\"main\",\"method\":\"a.B.m()\",\"durationMs\":60,\"depth\":0}\n"));
        assertTrue(html.contains("getElementById('report-data')"));
        assertTrue(html.contains("--accent"));
        assertFalse(html.contains("{{"));
    }

    @Test
    public void dataCannotCloseTheScriptElement() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "</script><b>", "a.B.<init>()", 60, 0));

        String html = render(new Report(records, 0, 50));

        assertTrue(html.contains("\\u003c/script>\\u003cb>"));
        assertTrue(html.contains("a.B.\\u003cinit>()"));
        assertEquals(2, html.split("</script>", -1).length - 1);
    }

    @Test
    public void placeholderTextInDataIsNotExpanded() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "t", "{{STYLE}}", 60, 0));

        String html = render(new Report(records, 0, 50));

        assertTrue(html.contains("\"method\":\"{{STYLE}}\""));
    }
}