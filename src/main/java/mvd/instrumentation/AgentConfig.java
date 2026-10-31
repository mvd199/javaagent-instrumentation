package mvd.instrumentation;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * Agent settings, read from system properties and validated on creation, so an instance is always well formed.
 * Pure data: the components built from it are created in {@link DurationAgent#premain}.
 * - Core agent configs: mvd.instrumentation.agent.threshold, mvd.instrumentation.agent.maxEvents
 * - Report configs: mvd.instrumentation.report.format, mvd.instrumentation.report.output,
 */
final class AgentConfig {
    static final String THRESHOLD_PROPERTY = "mvd.instrumentation.agent.threshold";
    static final String MAX_EVENTS_PROPERTY = "mvd.instrumentation.agent.maxEvents";
    static final String FORMAT_PROPERTY = "mvd.instrumentation.report.format";
    static final String OUTPUT_PROPERTY = "mvd.instrumentation.report.output";

    static final long DEFAULT_THRESHOLD = 50L;
    /** Rejects larger thresholds so a typo such as a day in milliseconds cannot silently disable recording. */
    static final long MAX_THRESHOLD = 180_000L;
    static final int DEFAULT_MAX_EVENTS = 100_000;
    static final ReportFormat DEFAULT_FORMAT = ReportFormat.TEXT;
    static final ReportOutput DEFAULT_OUTPUT = ReportOutput.STDOUT;

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

    /** Unset or blank properties take their default; set but invalid ones throw, naming the property. */
    static AgentConfig from(Function<String, String> properties) {
        long threshold = requireInRange(THRESHOLD_PROPERTY,
                readLong(properties, THRESHOLD_PROPERTY).orElse(DEFAULT_THRESHOLD), 0, MAX_THRESHOLD);
        // The range check guarantees the cast to int is safe.
        int maxEvents = (int) requireInRange(MAX_EVENTS_PROPERTY,
                readLong(properties, MAX_EVENTS_PROPERTY).orElse(DEFAULT_MAX_EVENTS), 1, Integer.MAX_VALUE);
        ReportFormat format = read(properties, FORMAT_PROPERTY, ReportFormat::parse, ReportFormat.usage())
                .orElse(DEFAULT_FORMAT);
        ReportOutput output = read(properties, OUTPUT_PROPERTY, ReportOutput::parse, ReportOutput.usage())
                .orElse(DEFAULT_OUTPUT);
        return new AgentConfig(threshold, maxEvents, format, output);
    }

    /** Reading only: the trimmed value, or empty when unset or blank. */
    private static Optional<String> readString(Function<String, String> properties, String name) {
        return Optional.ofNullable(properties.apply(name)).map(String::trim).filter(value -> !value.isEmpty());
    }

    /** Reading only: empty when unset or blank, throws if the value is not a number. */
    private static OptionalLong readLong(Function<String, String> properties, String name) {
        Optional<String> raw = readString(properties, name);
        if (!raw.isPresent()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(raw.get()));
        } catch (NumberFormatException e) {
            throw invalid(name, "an integer", raw.get());
        }
    }

    /** Reading only: empty when unset or blank, throws if {@code parser} rejects the value. */
    private static <T> Optional<T> read(Function<String, String> properties, String name,
                                        Function<String, Optional<T>> parser, String usage) {
        return readString(properties, name)
                .map(value -> parser.apply(value).orElseThrow(() -> invalid(name, usage, value)));
    }

    /** Validation only: throws, naming the property, when the value is outside [min, max]. */
    private static long requireInRange(String name, long value, long min, long max) {
        if (value < min || value > max) {
            String range = max == Long.MAX_VALUE ? "at least " + min : "between " + min + " and " + max;
            throw invalid(name, range, value);
        }
        return value;
    }

    /** Every invalid value is reported as {@code <property> must be <usage>: <value>}. */
    private static IllegalArgumentException invalid(String name, String usage, Object value) {
        return new IllegalArgumentException(name + " must be " + usage + ": " + value);
    }
}
