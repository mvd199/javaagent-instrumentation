package mvd.instrumentation;

import java.util.Arrays;
import java.util.Optional;

/** Report formats selected with {@link AgentConfig#FORMAT_PROPERTY}. */
enum ReportFormat {
    TEXT,
    JSON;

    /** Case-insensitive; empty if the value names no format. */
    static Optional<ReportFormat> parse(String value) {
        return Arrays.stream(values()).filter(format -> format.name().equalsIgnoreCase(value)).findFirst();
    }
}
