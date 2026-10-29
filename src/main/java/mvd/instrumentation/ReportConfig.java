package mvd.instrumentation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Function;

/**
 * Report settings, read from system properties:
 * - Core agent configs: mvd.instrumentation.agent.threshold, mvd.instrumentation.agent.maxEvents
 * - Report configs: mvd.instrumentation.report.format, mvd.instrumentation.report.output
 */
final class ReportConfig {
    static final String THRESHOLD_PROPERTY = "mvd.instrumentation.agent.threshold";
    static final String MAX_EVENTS_PROPERTY = "mvd.instrumentation.agent.maxEvents";
    static final String FORMAT_PROPERTY = "mvd.instrumentation.report.format";
    static final String OUTPUT_PROPERTY = "mvd.instrumentation.report.output";

    static final long DEFAULT_THRESHOLD = 50L;
    /** Caps the threshold so a typo such as a day in milliseconds cannot silently disable recording. */
    static final long MAX_THRESHOLD = 180_000L;
    static final String DEFAULT_FORMAT = "summary";
    static final String DEFAULT_OUTPUT = "stdout";
    static final int DEFAULT_MAX_EVENTS = 100_000;

    final long threshold;
    final String format;
    final String output;
    final int maxEvents;
    final Reporter reporter;

    private ReportConfig(String format, String output, int maxEvents, long threshold) {
        this.format = format;
        this.output = output;
        this.maxEvents = maxEvents;
        this.threshold = threshold;
        this.reporter = "json".equals(format) ? new JsonReporter() : new SummaryReporter();
    }

    static ReportConfig fromSystemProperties() {
        return from(System::getProperty);
    }

    static ReportConfig from(Function<String, String> properties) {
        String format = valueOrDefault(properties.apply(FORMAT_PROPERTY), DEFAULT_FORMAT).toLowerCase(Locale.ROOT);
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
        long threshold = DEFAULT_THRESHOLD;
        String rawThreshold = properties.apply(THRESHOLD_PROPERTY);
        if (rawThreshold != null && !rawThreshold.trim().isEmpty()) {
            try {
                threshold = Long.parseLong(rawThreshold.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(THRESHOLD_PROPERTY + " must be a non-negative integer: " + rawThreshold);
            }
            if (threshold < 0) {
                throw new IllegalArgumentException(THRESHOLD_PROPERTY + " must be a non-negative integer: " + rawThreshold);
            }
            if (threshold > MAX_THRESHOLD) {
                AgentLogger.warn(THRESHOLD_PROPERTY + " (" + threshold + " ms) exceeds maximum limit of "
                        + MAX_THRESHOLD + " ms (3 minutes); capping to " + MAX_THRESHOLD + " ms.");
                threshold = MAX_THRESHOLD;
            }
        }
        return new ReportConfig(format, output, maxEvents, threshold);
    }

    Reporter reporter() {
        return reporter;
    }

    /** Resolved at call time so a redirected System.out/err at shutdown is honoured. Closing never closes stdout/stderr. */
    PrintWriter openOutput() throws IOException {
        if ("stdout".equalsIgnoreCase(output)) {
            return nonClosing(System.out);
        }
        if ("stderr".equalsIgnoreCase(output)) {
            return nonClosing(System.err);
        }
        Path path = Paths.get(output);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
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
