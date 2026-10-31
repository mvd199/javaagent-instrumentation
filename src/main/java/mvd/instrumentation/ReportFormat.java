package mvd.instrumentation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Report formats selected with {@link AgentConfig#FORMAT_PROPERTY}. */
enum ReportFormat {
    TEXT,
    JSON,
    HTML;

    /** Case-insensitive; empty if the value names no format. */
    static Optional<ReportFormat> parse(String value) {
        return Arrays.stream(values()).filter(format -> format.name().equalsIgnoreCase(value)).findFirst();
    }

    /** What {@link #parse} accepts, for error messages and usage text, e.g. {@code "text or json"}. */
    static String usage() {
        StringBuilder sb = new StringBuilder();
        ReportFormat[] formats = values();
        for (int i = 0; i < formats.length; i++) {
            if (i > 0) {
                sb.append(i == formats.length - 1 ? " or " : ", ");
            }
            sb.append(formats[i].name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
