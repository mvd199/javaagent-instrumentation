package mvd.instrumentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.util.Set;
import java.util.stream.Collectors;

public class DurationAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        ClassFilter filter = createClassFilter(agentArgs);
        ReportConfig config = ReportConfig.fromSystemProperties();
        Recorder recorder = new Recorder(config.threshold, config.maxEvents, DurationTransformer::currentDepth);
        inst.addTransformer(new DurationTransformer(filter, recorder::record));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> writeReport(recorder, config), "duration-agent-report"));
    }

    static ClassFilter createClassFilter(String agentArgs) {
        return ClassFilter.fromFile(agentArgs);
    }

    static void writeReport(Recorder recorder, ReportConfig config) {
        Report report = recorder.snapshot();
        if (report.dropped > 0 && "json".equals(config.format)) {
            AgentLogger.warn("dropped " + report.dropped + " calls exceeding maxEvents (" + config.maxEvents + ")");
        }
        PrintWriter out = null;
        boolean error = false;
        try {
            out = config.openOutput();
            if (!writeTo(out, config.reporter(), report)) {
                error = true;
            }
        } catch (IOException | RuntimeException e) {
            AgentLogger.error("failed to write report to " + config.output, e);
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
            AgentLogger.error("report to " + config.output + " may be incomplete (write error)");
        }
    }

    /** Returns false if the writer hit an I/O error, which PrintWriter otherwise swallows. */
    static boolean writeTo(PrintWriter out, Reporter reporter, Report report) {
        reporter.write(report, out);
        out.flush();
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

        try (BufferedReader reader = new BufferedReader(new FileReader(configuration))) {
            return ClassFilter.parseLines(reader).stream()
                    .map(s -> s.replace('.', '/'))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read configuration.", e);
        }
    }
}
