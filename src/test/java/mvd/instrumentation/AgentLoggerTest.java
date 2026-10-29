package mvd.instrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

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
        AgentLogger.reset();
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
        AgentLogger.reset();
    }

    @Test
    public void defaultLevelIsInfoAndOutputsToStderr() {
        assertEquals(AgentLogger.Level.INFO, AgentLogger.getLevel());
        assertFalse(AgentLogger.isDebugEnabled());
        assertTrue(AgentLogger.isInfoEnabled());
        assertTrue(AgentLogger.isWarnEnabled());
        assertTrue(AgentLogger.isErrorEnabled());

        AgentLogger.info("default info message");

        assertEquals("", outBytes.toString());
        String err = errBytes.toString();
        assertTrue(err, err.contains("DurationAgent:"));
        assertTrue(err, err.contains("[INFO] default info message"));
        assertTrue(err, err.matches("(?s).*DurationAgent: \\[\\+\\d+ms\\] \\[INFO\\] default info message.*"));
    }

    @Test
    public void debugIsSuppressedWhenLevelIsInfo() {
        AgentLogger.setLevel(AgentLogger.Level.INFO);

        AgentLogger.debug("should not appear");

        assertEquals("", errBytes.toString());
    }

    @Test
    public void debugIsEmittedWhenLevelIsDebug() {
        AgentLogger.setLevel(AgentLogger.Level.DEBUG);
        assertTrue(AgentLogger.isDebugEnabled());

        AgentLogger.debug("debug message");

        String err = errBytes.toString();
        assertTrue(err, err.contains("[DEBUG] debug message"));
    }

    @Test
    public void offSuppressesAllMessages() {
        AgentLogger.setLevel(AgentLogger.Level.OFF);
        assertFalse(AgentLogger.isErrorEnabled());

        AgentLogger.error("error message");
        AgentLogger.warn("warn message");
        AgentLogger.info("info message");
        AgentLogger.debug("debug message");

        assertEquals("", errBytes.toString());
    }

    @Test
    public void warnAndErrorLevelsFilterAppropriately() {
        AgentLogger.setLevel(AgentLogger.Level.WARN);
        assertFalse(AgentLogger.isInfoEnabled());
        assertTrue(AgentLogger.isWarnEnabled());
        assertTrue(AgentLogger.isErrorEnabled());

        AgentLogger.info("ignored");
        AgentLogger.warn("warning");
        AgentLogger.error("error");

        String err = errBytes.toString();
        assertFalse(err.contains("ignored"));
        assertTrue(err.contains("[WARN] warning"));
        assertTrue(err.contains("[ERROR] error"));
    }

    @Test
    public void logsExceptionStackTraceWhenProvided() {
        AgentLogger.setLevel(AgentLogger.Level.ERROR);

        AgentLogger.error("failure occurred", new RuntimeException("test exception"));

        String err = errBytes.toString();
        assertTrue(err, err.contains("[ERROR] failure occurred"));
        assertTrue(err, err.contains("java.lang.RuntimeException: test exception"));
    }

    @Test
    public void relativeElapsedOffsetIsAccurate() {
        // Set start time 100ms in the past
        long now = System.nanoTime();
        AgentLogger.setStartTimeNanos(now - 100_000_000L);

        AgentLogger.info("timing check");

        String err = errBytes.toString();
        assertTrue(err, err.matches("(?s).*DurationAgent: \\[\\+\\d+ms\\] \\[INFO\\] timing check.*"));
    }

    @Test
    public void initFromPropertiesConfiguresLevelAndStdout() {
        Map<String, String> props = new HashMap<>();
        props.put(AgentLogger.LOG_LEVEL_PROPERTY, "DEBUG");
        props.put(AgentLogger.LOG_OUTPUT_PROPERTY, "stdout");

        AgentLogger.initFromProperties(props::get);

        assertEquals(AgentLogger.Level.DEBUG, AgentLogger.getLevel());
        AgentLogger.debug("sent to stdout");

        assertTrue(outBytes.toString().contains("[DEBUG] sent to stdout"));
        assertEquals("", errBytes.toString());
    }

    @Test
    public void initFromPropertiesFallsBackOnInvalidLevel() {
        Map<String, String> props = new HashMap<>();
        props.put(AgentLogger.LOG_LEVEL_PROPERTY, "NOT_A_LEVEL");

        AgentLogger.initFromProperties(props::get);

        assertEquals(AgentLogger.Level.INFO, AgentLogger.getLevel());
    }

    @Test
    public void targetStreamCanBeExplicitlySet() {
        ByteArrayOutputStream customBytes = new ByteArrayOutputStream();
        PrintStream customStream = new PrintStream(customBytes);

        AgentLogger.setTargetStream(customStream);
        AgentLogger.info("custom target");

        assertTrue(customBytes.toString().contains("[INFO] custom target"));
        assertEquals("", errBytes.toString());
    }
}
