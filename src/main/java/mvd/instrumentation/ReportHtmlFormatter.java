package mvd.instrumentation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A single self-contained HTML page. It only injects data: the markup, rendering script and (replaceable) stylesheet
 * are static resources, so the look can change without touching how the records are presented.
 * The page is {@code report.html} with three placeholders filled in: {@code {{STYLE}}}, {@code {{SCRIPT}}} and
 * {@code {{DATA}}} (a JSON object read by the script).
 */
final class ReportHtmlFormatter implements ReportFormatter {
    private static final String RESOURCE_DIR = "/mvd/instrumentation/";

    private final Optional<Path> stylesheet;

    ReportHtmlFormatter(Optional<Path> stylesheet) {
        this.stylesheet = stylesheet;
    }

    @Override
    public void write(Report report, PrintWriter out) {
        try {
            String css = stylesheet.isPresent()
                    ? new String(Files.readAllBytes(stylesheet.get()), StandardCharsets.UTF_8)
                    : resource("report.css");
            // Data goes in last, so nothing in it can be mistaken for a placeholder.
            out.print(resource("report.html")
                    .replace("{{STYLE}}", css)
                    .replace("{{SCRIPT}}", resource("report.js"))
                    .replace("{{DATA}}", data(report)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read HTML report resources", e);
        }
    }

    /** JSON safe to embed in a script element: every '<' is escaped, so no string can close the element. */
    static String data(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"thresholdMs\":").append(report.thresholdMs);
        sb.append(",\"dropped\":").append(report.dropped);
        sb.append(",\"records\":[");
        boolean first = true;
        for (ReportRecord call : report.inCallOrder()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            ReportJsonFormatter.appendRecord(call, sb);
        }
        sb.append("]}");
        return sb.toString().replace("<", "\\u003c");
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
