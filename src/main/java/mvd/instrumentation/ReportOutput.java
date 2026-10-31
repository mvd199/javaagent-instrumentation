package mvd.instrumentation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/** Where the report goes: stdout, stderr or a file. */
final class ReportOutput {
    enum Kind { STDOUT, STDERR, FILE }

    static final ReportOutput STDOUT = new ReportOutput(Kind.STDOUT, null);
    static final ReportOutput STDERR = new ReportOutput(Kind.STDERR, null);

    final Kind kind;
    private final Path file;

    private ReportOutput(Kind kind, Path file) {
        this.kind = kind;
        this.file = file;
    }

    /** {@code stdout} and {@code stderr} are case-insensitive; anything else is a file path. */
    static ReportOutput parse(String value) {
        if ("stdout".equalsIgnoreCase(value)) {
            return STDOUT;
        }
        if ("stderr".equalsIgnoreCase(value)) {
            return STDERR;
        }
        return new ReportOutput(Kind.FILE, Paths.get(value));
    }

    /** Present only for {@link Kind#FILE}. */
    Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    @Override
    public String toString() {
        return kind == Kind.FILE ? file.toString() : kind.name().toLowerCase(Locale.ROOT);
    }
}
