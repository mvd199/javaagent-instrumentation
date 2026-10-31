package mvd.instrumentation;

import java.io.PrintWriter;
import java.util.Locale;

/**
 * JSON lines (always {@code \n}-terminated, whatever the platform), one object per slow call, ordered so parents precede their children.
 */
final class ReportJsonFormatter implements ReportFormatter {
    @Override
    public void write(Report report, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        for (ReportRecord call : report.inCallOrder()) {
            sb.setLength(0);
            appendRecord(call, sb);
            sb.append('\n');
            out.print(sb.toString());
        }
    }

    /** One call as a JSON object, shared with {@link ReportHtmlFormatter} so both formats carry the same fields. */
    static void appendRecord(ReportRecord call, StringBuilder sb) {
        sb.append("{\"timestamp\":").append(call.timestampMillis);
        sb.append(",\"thread\":");
        quote(call.thread, sb);
        sb.append(",\"method\":");
        quote(call.method, sb);
        sb.append(",\"durationMs\":").append(call.durationMs);
        sb.append(",\"depth\":").append(call.depth);
        sb.append('}');
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
