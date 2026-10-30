package mvd.instrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgentLoggerTest {
    private ByteArrayOutputStream outBytes;
    private ByteArrayOutputStream errBytes;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @Before
    public void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes));
        System.setErr(new PrintStream(errBytes));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private static AgentLogger logger(AgentLogger.Level level) {
        return new AgentLogger(level, System.nanoTime());
    }

    @Test
    public void infoOutputsToStderrOnly() {
        logger(AgentLogger.Level.INFO).info("default info message");

        assertEquals("", outBytes.toString());
        String err = errBytes.toString();
        assertTrue(err, err.matches("(?s)DurationAgent: \\[\\+\\d+ms\\] \\[INFO\\] default info message.*"));
    }

    @Test
    public void debugIsSuppressedAndNotBuiltWhenLevelIsInfo() {
        logger(AgentLogger.Level.INFO).debug(() -> {
            throw new AssertionError("message must not be built");
        });

        assertEquals("", errBytes.toString());
    }

    @Test
    public void debugIsEmittedWhenLevelIsDebug() {
        logger(AgentLogger.Level.DEBUG).debug(() -> "debug message");

        String err = errBytes.toString();
        assertTrue(err, err.contains("[DEBUG] debug message"));
    }

    @Test
    public void offSuppressesAllMessages() {
        AgentLogger log = logger(AgentLogger.Level.OFF);

        log.error("error message");
        log.warn("warn message");
        log.info("info message");
        log.debug(() -> "debug message");

        assertEquals("", errBytes.toString());
    }

    @Test
    public void warnAndErrorLevelsFilterAppropriately() {
        AgentLogger log = logger(AgentLogger.Level.WARN);

        log.info("ignored");
        log.warn("warning");
        log.error("error");

        String err = errBytes.toString();
        assertFalse(err.contains("ignored"));
        assertTrue(err.contains("[WARN] warning"));
        assertTrue(err.contains("[ERROR] error"));
    }

    @Test
    public void logsExceptionStackTraceWhenProvided() {
        logger(AgentLogger.Level.ERROR).error("failure occurred", new RuntimeException("test exception"));

        String err = errBytes.toString();
        assertTrue(err, err.contains("[ERROR] failure occurred"));
        assertTrue(err, err.contains("java.lang.RuntimeException: test exception"));
    }

    @Test
    public void elapsedTimeIsMeasuredFromStart() {
        new AgentLogger(AgentLogger.Level.INFO, System.nanoTime() - 100_000_000L).info("timing check");

        Matcher m = Pattern.compile("\\[\\+(\\d+)ms\\] \\[INFO\\] timing check").matcher(errBytes.toString());
        assertTrue(errBytes.toString(), m.find());
        assertTrue(m.group(1), Long.parseLong(m.group(1)) >= 100);
    }

    @Test
    public void parsesLevelCaseInsensitively() {
        assertEquals(AgentLogger.Level.DEBUG, AgentLogger.Level.parse(" debug ", AgentLogger.Level.INFO));
    }

    @Test
    public void parseFallsBackOnMissingOrInvalidLevel() {
        assertEquals(AgentLogger.Level.INFO, AgentLogger.Level.parse(null, AgentLogger.Level.INFO));
        assertEquals(AgentLogger.Level.INFO, AgentLogger.Level.parse("", AgentLogger.Level.INFO));
        assertEquals(AgentLogger.Level.INFO, AgentLogger.Level.parse("NOT_A_LEVEL", AgentLogger.Level.INFO));
    }
}
