package mvd.instrumentation;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Function;

/**
 * Lightweight, zero-dependency diagnostic logger for DurationAgent.
 *
 * <p>Diagnostic logs are kept separate from profiler report output to prevent corrupting
 * structured output formats (e.g. JSON lines on stdout). All diagnostic logs default to
 * {@code System.err}, stamped with the time elapsed since this class was initialized (in practice,
 * shortly after agent startup).
 */
final class AgentLogger {
    public enum Level {
        OFF(0),
        ERROR(1),
        WARN(2),
        INFO(3),
        DEBUG(4);

        final int severity;

        Level(int severity) {
            this.severity = severity;
        }

        public static Level fromString(String val, Level defaultLevel) {
            if (val == null || val.trim().isEmpty()) {
                return defaultLevel;
            }
            try {
                return Level.valueOf(val.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return defaultLevel;
            }
        }
    }

    public static final String LOG_LEVEL_PROPERTY = "mvd.instrumentation.log.level";
    public static final String LOG_OUTPUT_PROPERTY = "mvd.instrumentation.log.output";

    private static volatile long startTimeNanos = System.nanoTime();
    private static volatile Level currentLevel = Level.INFO;
    private static volatile boolean useStdout = false;
    private static volatile PrintStream customStream = null;

    static {
        initFromProperties(System::getProperty);
    }

    private AgentLogger() {
    }

    static void initFromProperties(Function<String, String> properties) {
        String lvlStr = properties.apply(LOG_LEVEL_PROPERTY);
        currentLevel = Level.fromString(lvlStr, Level.INFO);

        String outStr = properties.apply(LOG_OUTPUT_PROPERTY);
        useStdout = outStr != null && "stdout".equalsIgnoreCase(outStr.trim());
        customStream = null;
    }

    public static Level getLevel() {
        return currentLevel;
    }

    public static boolean isDebugEnabled() {
        return currentLevel.severity >= Level.DEBUG.severity;
    }

    public static boolean isInfoEnabled() {
        return currentLevel.severity >= Level.INFO.severity;
    }

    public static boolean isWarnEnabled() {
        return currentLevel.severity >= Level.WARN.severity;
    }

    public static boolean isErrorEnabled() {
        return currentLevel.severity >= Level.ERROR.severity;
    }

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    private static void log(Level level, String message, Throwable throwable) {
        if (currentLevel.severity >= level.severity) {
            long elapsedMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
            if (elapsedMs < 0) {
                elapsedMs = 0;
            }
            PrintStream out = resolveStream();
            out.println("DurationAgent: [+" + elapsedMs + "ms] [" + level + "] " + message);
            if (throwable != null) {
                throwable.printStackTrace(out);
            }
        }
    }

    private static PrintStream resolveStream() {
        if (customStream != null) {
            return customStream;
        }
        return useStdout ? System.out : System.err;
    }

    // Testing helpers
    static void setLevel(Level level) {
        currentLevel = level != null ? level : Level.INFO;
    }

    static void setTargetStream(PrintStream stream) {
        customStream = stream;
    }

    static void setStartTimeNanos(long nanos) {
        startTimeNanos = nanos;
    }

    static void reset() {
        startTimeNanos = System.nanoTime();
        initFromProperties(System::getProperty);
    }
}
