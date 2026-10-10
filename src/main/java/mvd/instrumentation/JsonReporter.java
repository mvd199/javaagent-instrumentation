package mvd.instrumentation;

import java.io.PrintWriter;

/**
 * JSON lines (always {@code \n}-terminated, whatever the platform), one object per slow call, ordered so parents precede their children.
 * If records were dropped at the buffer cap, a final {@code {"dropped":N}} line is added.
 */
final class JsonReporter implements Reporter {
    @Override
    public void write(Report report, PrintWriter out) {
        for (CallRecord call : report.inCallOrder()) {
            out.print("{\"timestamp\":");
            out.print(call.timestampMillis);
            out.print(",\"thread\":");
            quote(call.thread, out);
            out.print(",\"method\":");
            quote(call.method, out);
            out.print(",\"durationMs\":");
            out.print(call.durationMs);
            out.print(",\"depth\":");
            out.print(call.depth);
            out.print("}\n");
        }
        if (report.dropped > 0) {
            out.print("{\"dropped\":" + report.dropped + "}\n");
        }
    }

    private static void quote(String value, PrintWriter out) {
        out.print('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.print("\\\""); break;
                case '\\': out.print("\\\\"); break;
                case '\n': out.print("\\n"); break;
                case '\r': out.print("\\r"); break;
                case '\t': out.print("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.print(String.format("\\u%04x", (int) c));
                    } else {
                        out.print(c);
                    }
            }
        }
        out.print('"');
    }
}
