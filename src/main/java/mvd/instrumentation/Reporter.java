package mvd.instrumentation;

import java.io.PrintWriter;

/** Renders a {@link Report} in one output format. */
interface Reporter {
    void write(Report report, PrintWriter out);
}
