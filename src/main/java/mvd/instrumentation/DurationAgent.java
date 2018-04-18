package mvd.instrumentation;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DurationAgent {
    private static final int MINIMAL_INVOCATION_TIME_MILLIS = 50;

    public static void premain(String agentArgs, Instrumentation inst) {
        Set<String> whiteList = createWhitelist(agentArgs);
        inst.addTransformer(new DurationTransformer(whiteList::contains, DurationAgent::registerTime));
    }

    private static Set<String> createWhitelist(String agentArgs) {
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

    private static void registerTime(final String methodName, final long startTimeNanos) {
        final long durationInMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        if (durationInMs > MINIMAL_INVOCATION_TIME_MILLIS) {
            System.out.println(methodName + " : " + durationInMs);
        }
    }
}
