package mvd.instrumentation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReportHtmlFormatterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static String render(Report report, Optional<java.nio.file.Path> stylesheet) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        new ReportHtmlFormatter(stylesheet).write(report, out);
        out.flush();
        return text.toString();
    }

    @Test
    public void embedsDataScriptAndBuiltInStylesheet() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1000L, "main", "a.B.m()", 60, 0));

        String html = render(new Report(records, 3, 50), Optional.empty());

        assertTrue(html, html.contains("{\"thresholdMs\":50,\"dropped\":3,\"records\":["
                + "{\"timestamp\":1000,\"thread\":\"main\",\"method\":\"a.B.m()\",\"durationMs\":60,\"depth\":0}]}"));
        assertTrue(html.contains("getElementById('report-data')"));
        assertTrue(html.contains("--accent"));
        assertFalse(html.contains("{{"));
    }

    @Test
    public void dataCannotCloseTheScriptElement() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "</script><b>", "a.B.<init>()", 60, 0));

        String html = render(new Report(records, 0, 50), Optional.empty());

        assertTrue(html.contains("\\u003c/script>\\u003cb>"));
        assertTrue(html.contains("a.B.\\u003cinit>()"));
        assertEquals(2, html.split("</script>", -1).length - 1);
    }

    @Test
    public void placeholderTextInDataIsNotExpanded() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "t", "{{STYLE}}", 60, 0));

        String html = render(new Report(records, 0, 50), Optional.empty());

        assertTrue(html.contains("\"method\":\"{{STYLE}}\""));
    }

    @Test
    public void customStylesheetReplacesBuiltInOne() throws Exception {
        File css = folder.newFile("custom.css");
        Files.write(css.toPath(), "body{color:hotpink}".getBytes(StandardCharsets.UTF_8));

        String html = render(new Report(Collections.<ReportRecord>emptyList(), 0, 50), Optional.of(css.toPath()));

        assertTrue(html.contains("body{color:hotpink}"));
        assertFalse(html.contains("--accent"));
    }

    @Test(expected = java.io.UncheckedIOException.class)
    public void missingStylesheetFails() {
        render(new Report(Collections.<ReportRecord>emptyList(), 0, 50),
                Optional.of(new File(folder.getRoot(), "none.css").toPath()));
    }
}
