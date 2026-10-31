package mvd.instrumentation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a {@link Report} in the configured format to the configured output. Failures are logged, never thrown. */
final class ReportWriter {
    private final AgentConfig config;
    private final ReportFormatter formatter;

    ReportWriter(AgentConfig config) {
        this.config = config;
        this.formatter = config.format == ReportFormat.JSON ? new ReportJsonFormatter() : new ReportTextFormatter();
    }

    void write(Report report) {
        // The text header shows dropped calls; JSON lines have nowhere to put them.
        if (report.dropped > 0 && config.format == ReportFormat.JSON) {
            AgentLogger.LOG.warn("dropped " + report.dropped + " calls exceeding maxEvents (" + config.maxEvents + ")");
        }
        PrintWriter out = null;
        boolean error = false;
        try {
            out = open(config.output);
            if (!writeTo(out, formatter, report)) {
                error = true;
            }
        } catch (IOException | RuntimeException e) {
            AgentLogger.LOG.error("failed to write report to " + config.output, e);
            return;
        } finally {
            if (out != null) {
                out.close();
                if (out.checkError()) {
                    error = true;
                }
            }
        }
        if (error) {
            AgentLogger.LOG.error("report to " + config.output + " may be incomplete (write error)");
        }
    }

    /** Returns false if the writer hit an I/O error, which PrintWriter otherwise swallows. */
    static boolean writeTo(PrintWriter out, ReportFormatter formatter, Report report) {
        formatter.write(report, out);
        out.flush();
        return !out.checkError();
    }

    /** Resolved at call time so a redirected System.out/err at shutdown is honoured. Closing never closes stdout/stderr. */
    static PrintWriter open(ReportOutput output) throws IOException {
        switch (output.kind) {
            case STDOUT:
                return nonClosing(System.out);
            case STDERR:
                return nonClosing(System.err);
            default:
                Path file = output.file.get();
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                return new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
        }
    }

    private static PrintWriter nonClosing(PrintStream stream) {
        return new PrintWriter((OutputStream) stream) {
            @Override
            public void close() {
                flush();
            }
        };
    }
}
