package mvd.instrumentation;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;

public class DurationAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        CoreClassFilter filter = createClassFilter(agentArgs);
        AgentConfig config = AgentConfig.fromSystemProperties();
        CoreRecorder recorder = new CoreRecorder(config.threshold, config.maxEvents, CoreTransformer::currentDepth);
        inst.addTransformer(new CoreTransformer(filter, recorder::record));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> writeReport(recorder, config), "duration-agent-report"));
    }

    static CoreClassFilter createClassFilter(String agentArgs) {
        return CoreClassFilter.fromFile(agentArgs);
    }

    static void writeReport(CoreRecorder recorder, AgentConfig config) {
        Report report = recorder.snapshot();
        if (report.dropped > 0 && "json".equals(config.format)) {
            AgentLogger.LOG.warn("dropped " + report.dropped + " calls exceeding maxEvents (" + config.maxEvents + ")");
        }
        PrintWriter out = null;
        boolean error = false;
        try {
            out = config.openOutput();
            if (!writeTo(out, config.reporter(), report)) {
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
    static boolean writeTo(PrintWriter out, ReportFormatter reporter, Report report) {
        reporter.write(report, out);
        out.flush();
        return !out.checkError();
    }
}
