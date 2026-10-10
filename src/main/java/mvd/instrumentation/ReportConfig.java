package mvd.instrumentation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;

/** Report settings, read from system properties: duration.format, duration.output, duration.maxEvents. */
final class ReportConfig {
    static final String FORMAT_PROPERTY = "duration.format";
    static final String OUTPUT_PROPERTY = "duration.output";
    static final String MAX_EVENTS_PROPERTY = "duration.maxEvents";

    static final String DEFAULT_FORMAT = "summary";
    static final String DEFAULT_OUTPUT = "stdout";
    static final int DEFAULT_MAX_EVENTS = 100_000;

    final String format;
    final String output;
    final int maxEvents;

    private ReportConfig(String format, String output, int maxEvents) {
        this.format = format;
        this.output = output;
        this.maxEvents = maxEvents;
    }

    static ReportConfig fromSystemProperties() {
        return from(System::getProperty);
    }

    static ReportConfig from(Function<String, String> properties) {
        String format = valueOrDefault(properties.apply(FORMAT_PROPERTY), DEFAULT_FORMAT);
        if (!"summary".equals(format) && !"json".equals(format)) {
            throw new IllegalArgumentException("Unknown " + FORMAT_PROPERTY + ": " + format + " (expected summary or json)");
        }
        String output = valueOrDefault(properties.apply(OUTPUT_PROPERTY), DEFAULT_OUTPUT);
        int maxEvents = DEFAULT_MAX_EVENTS;
        String rawMax = properties.apply(MAX_EVENTS_PROPERTY);
        if (rawMax != null && !rawMax.trim().isEmpty()) {
            try {
                maxEvents = Integer.parseInt(rawMax.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(MAX_EVENTS_PROPERTY + " must be a positive integer: " + rawMax);
            }
            if (maxEvents <= 0) {
                throw new IllegalArgumentException(MAX_EVENTS_PROPERTY + " must be a positive integer: " + rawMax);
            }
        }
        return new ReportConfig(format, output, maxEvents);
    }

    Reporter reporter() {
        return "json".equals(format) ? new JsonReporter() : new SummaryReporter();
    }

    /** Resolved at call time so a redirected System.out/err at shutdown is honoured. Closing never closes stdout/stderr. */
    PrintWriter openOutput() throws IOException {
        if ("stdout".equals(output)) {
            return nonClosing(System.out);
        }
        if ("stderr".equals(output)) {
            return nonClosing(System.err);
        }
        return new PrintWriter(Files.newBufferedWriter(Paths.get(output), StandardCharsets.UTF_8));
    }

    private static PrintWriter nonClosing(PrintStream stream) {
        return new PrintWriter((OutputStream) stream) {
            @Override
            public void close() {
                flush();
            }
        };
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
