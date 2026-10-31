package mvd.instrumentation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A single self-contained HTML page. It only injects data: the markup, rendering script and stylesheet
 * are static resources, so the look can change without touching how the records are presented.
 * The page is {@code report.html} with three placeholders filled in: {@code {{STYLE}}}, {@code {{SCRIPT}}} and
 * {@code {{DATA}}} (the report as JSON lines, read by the script).
 */
final class ReportHtmlFormatter implements ReportFormatter {
    private static final String RESOURCE_DIR = "/mvd/instrumentation/";

    @Override
    public void write(Report report, PrintWriter out) {
        try {
            // Data goes in last, so nothing in it can be mistaken for a placeholder.
            out.print(resource("report.html")
                    .replace("{{STYLE}}", resource("report.css"))
                    .replace("{{SCRIPT}}", resource("report.js"))
                    .replace("{{DATA}}", data(report)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read HTML report resources", e);
        }
    }

    /** The JSON lines of {@link ReportJsonFormatter}, safe to embed in a script element: every '<' is escaped. */
    static String data(Report report) {
        StringWriter lines = new StringWriter();
        PrintWriter out = new PrintWriter(lines);
        new ReportJsonFormatter().write(report, out);
        out.flush();
        return lines.toString().replace("<", "\\u003c");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ReportHtmlFormatter.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) {
                throw new IOException("missing resource " + RESOURCE_DIR + name);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; (n = in.read(chunk)) > 0; ) {
                buffer.write(chunk, 0, n);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
