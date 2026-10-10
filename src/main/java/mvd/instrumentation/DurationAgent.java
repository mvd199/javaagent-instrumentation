package mvd.instrumentation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

public class DurationAgent {
    private static final int MINIMAL_INVOCATION_TIME_MILLIS = 50;

    public static void premain(String agentArgs, Instrumentation inst) {
        Set<String> whiteList = createWhitelist(agentArgs);
        ReportConfig config = ReportConfig.fromSystemProperties();
        Recorder recorder = new Recorder(MINIMAL_INVOCATION_TIME_MILLIS, config.maxEvents, DurationTransformer::currentDepth);
        inst.addTransformer(new DurationTransformer(whiteList::contains, recorder::record));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> writeReport(recorder, config), "duration-agent-report"));
    }

    static void writeReport(Recorder recorder, ReportConfig config) {
        try (PrintWriter out = config.openOutput()) {
            if (!writeTo(out, config.reporter(), recorder.snapshot())) {
                System.err.println("DurationAgent: report to " + config.output + " may be incomplete (write error)");
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("DurationAgent: failed to write report to " + config.output);
            e.printStackTrace();
        }
    }

    /** Returns false if the writer hit an I/O error, which PrintWriter otherwise swallows. */
    static boolean writeTo(PrintWriter out, Reporter reporter, Report report) {
        reporter.write(report, out);
        return !out.checkError();
    }

    static Set<String> createWhitelist(String agentArgs) {
        if (agentArgs == null) {
            throw new IllegalArgumentException("Agent args can't be null.");
        }
        if ("".equals(agentArgs)) {
            throw new IllegalArgumentException("Agent args can't be empty.");
        }
        File configuration = new File(agentArgs);
        if (!configuration.exists()) {
            throw new IllegalArgumentException("Configuration file not found: " + agentArgs);
        }

        try {
            return Files.lines(configuration.toPath())
                    .map(s -> s.replace('.', '/'))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read configuration.", e);
        }
    }
}
