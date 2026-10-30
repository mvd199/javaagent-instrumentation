package mvd.instrumentation;

import java.util.Locale;
import java.util.function.Function;

/**
 * Agent settings, read from system properties and validated on creation, so an instance is always well formed.
 * Pure data: the components built from it are created in {@link DurationAgent#premain}.
 * - Core agent configs: mvd.instrumentation.agent.threshold, mvd.instrumentation.agent.maxEvents
 * - Report configs: mvd.instrumentation.report.format, mvd.instrumentation.report.output
 */
final class AgentConfig {
    static final String THRESHOLD_PROPERTY = "mvd.instrumentation.agent.threshold";
    static final String MAX_EVENTS_PROPERTY = "mvd.instrumentation.agent.maxEvents";
    static final String FORMAT_PROPERTY = "mvd.instrumentation.report.format";
    static final String OUTPUT_PROPERTY = "mvd.instrumentation.report.output";

    static final long DEFAULT_THRESHOLD = 50L;
    /** Rejects larger thresholds so a typo such as a day in milliseconds cannot silently disable recording. */
    static final long MAX_THRESHOLD = 180_000L;
    static final String DEFAULT_FORMAT = "summary";
    static final String DEFAULT_OUTPUT = "stdout";
    static final int DEFAULT_MAX_EVENTS = 100_000;

    final long threshold;
    final int maxEvents;
    final ReportFormat format;
    final ReportOutput output;

    private AgentConfig(long threshold, int maxEvents, ReportFormat format, ReportOutput output) {
        this.threshold = threshold;
        this.maxEvents = maxEvents;
        this.format = format;
        this.output = output;
    }

    static AgentConfig fromSystemProperties() {
        return from(System::getProperty);
    }

    static AgentConfig from(Function<String, String> properties) {
        long threshold = requireInRange(THRESHOLD_PROPERTY,
                readLong(properties, THRESHOLD_PROPERTY, DEFAULT_THRESHOLD), 0, MAX_THRESHOLD);
        // The range check guarantees the cast to int is safe.
        int maxEvents = (int) requireInRange(MAX_EVENTS_PROPERTY,
                readLong(properties, MAX_EVENTS_PROPERTY, DEFAULT_MAX_EVENTS), 1, Integer.MAX_VALUE);
        ReportFormat format = parseFormat(readString(properties, FORMAT_PROPERTY, DEFAULT_FORMAT));
        ReportOutput output = ReportOutput.parse(readString(properties, OUTPUT_PROPERTY, DEFAULT_OUTPUT));
        return new AgentConfig(threshold, maxEvents, format, output);
    }

    /** Reading only: returns the default when unset or blank, otherwise the trimmed value. */
    private static String readString(Function<String, String> properties, String name, String defaultValue) {
        String raw = properties.apply(name);
        return raw == null || raw.trim().isEmpty() ? defaultValue : raw.trim();
    }

    /** Reading only: returns the default when unset or blank, throws if the value is not a number. */
    private static long readLong(Function<String, String> properties, String name, long defaultValue) {
        String raw = readString(properties, name, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer: " + raw);
        }
    }

    /** Validation only: throws, naming the property, when the value is outside [min, max]. */
    private static long requireInRange(String name, long value, long min, long max) {
        if (value < min || value > max) {
            String range = max == Long.MAX_VALUE ? "at least " + min : "between " + min + " and " + max;
            throw new IllegalArgumentException(name + " must be " + range + ": " + value);
        }
        return value;
    }

    private static ReportFormat parseFormat(String value) {
        try {
            return ReportFormat.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(FORMAT_PROPERTY + " must be summary or json: " + value);
        }
    }
}
