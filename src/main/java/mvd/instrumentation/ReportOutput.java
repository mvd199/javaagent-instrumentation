package mvd.instrumentation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/** Where the report goes: stdout, stderr or a file. A union of the three: {@link #file} is set only for {@link Kind#FILE}. */
final class ReportOutput {
    enum Kind { STDOUT, STDERR, FILE }

    static final ReportOutput STDOUT = new ReportOutput(Kind.STDOUT, Optional.empty());
    static final ReportOutput STDERR = new ReportOutput(Kind.STDERR, Optional.empty());

    final Kind kind;
    final Optional<Path> file;

    private ReportOutput(Kind kind, Optional<Path> file) {
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
        return new ReportOutput(Kind.FILE, Optional.of(Paths.get(value)));
    }

    @Override
    public String toString() {
        return file.map(Path::toString).orElse(kind.name().toLowerCase(Locale.ROOT));
    }
}
