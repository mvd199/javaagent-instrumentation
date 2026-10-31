package mvd.instrumentation;

import java.io.PrintWriter;
import java.util.Locale;

/**
 * JSON lines (always {@code \n}-terminated, whatever the platform). The first line is a {@code meta} object with the
 * report-level fields; one {@code call} object per slow call follows, ordered so parents precede their children.
 * Also the data model embedded in the HTML report, so both formats carry the same thing.
 */
final class ReportJsonFormatter implements ReportFormatter {
    @Override
    public void write(Report report, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"meta\",\"thresholdMs\":").append(report.thresholdMs);
        sb.append(",\"dropped\":").append(report.dropped);
        sb.append("}\n");
        out.print(sb.toString());
        for (ReportRecord call : report.inCallOrder()) {
            sb.setLength(0);
            sb.append("{\"type\":\"call\",\"timestamp\":").append(call.timestampMillis);
            sb.append(",\"thread\":");
            quote(call.thread, sb);
            sb.append(",\"method\":");
            quote(call.method, sb);
            sb.append(",\"durationMs\":").append(call.durationMs);
            sb.append(",\"depth\":").append(call.depth);
            sb.append("}\n");
            out.print(sb.toString());
        }
    }

    private static void quote(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
