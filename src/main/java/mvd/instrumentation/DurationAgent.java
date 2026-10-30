package mvd.instrumentation;

import java.lang.instrument.Instrumentation;

public class DurationAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        CoreClassFilter filter = CoreClassFilter.fromFile(agentArgs);
        AgentConfig config = AgentConfig.fromSystemProperties();
        CoreRecorder recorder = new CoreRecorder(config.threshold, config.maxEvents);
        ReportWriter writer = new ReportWriter(config);

        CoreCallTracker.install(recorder::record);
        inst.addTransformer(new CoreTransformer(filter));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> writer.write(recorder.snapshot()), "duration-agent-report"));
    }
}
