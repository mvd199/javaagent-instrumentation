package mvd.instrumentation;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Lightweight, zero-dependency diagnostic logger for DurationAgent.
 *
 * <p>Diagnostic logs go to {@code System.err}, so they never mix with a profiler report written to stdout.
 * Each line is stamped with the time elapsed since the logger was created (in practice, shortly after
 * agent startup).
 */
final class AgentLogger {
    enum Level {
        OFF, ERROR, WARN, INFO, DEBUG;

        static Level parse(String value, Level defaultLevel) {
            if (value == null || value.trim().isEmpty()) {
                return defaultLevel;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return defaultLevel;
            }
        }
    }

    static final String LEVEL_PROPERTY = "mvd.instrumentation.log.level";

    static final AgentLogger LOG = new AgentLogger(
            Level.parse(System.getProperty(LEVEL_PROPERTY), Level.INFO), System.nanoTime());

    private final Level level;
    private final long startNanos;

    AgentLogger(Level level, long startNanos) {
        this.level = level;
        this.startNanos = startNanos;
    }

    /** The message is only built when DEBUG is enabled. */
    void debug(Supplier<String> message) {
        if (enabled(Level.DEBUG)) {
            log(Level.DEBUG, message.get(), null);
        }
    }

    void info(String message) {
        log(Level.INFO, message, null);
    }

    void warn(String message) {
        log(Level.WARN, message, null);
    }

    void error(String message) {
        log(Level.ERROR, message, null);
    }

    void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    private boolean enabled(Level messageLevel) {
        return level.compareTo(messageLevel) >= 0;
    }

    private void log(Level messageLevel, String message, Throwable throwable) {
        if (!enabled(messageLevel)) {
            return;
        }
        long elapsedMs = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
        // Resolved at call time so a redirected System.err is honoured.
        PrintStream out = System.err;
        // Keeps the message and its stack trace together when several threads log at once.
        synchronized (out) {
            out.println("DurationAgent: [+" + elapsedMs + "ms] [" + messageLevel + "] " + message);
            if (throwable != null) {
                throwable.printStackTrace(out);
            }
        }
    }
}
