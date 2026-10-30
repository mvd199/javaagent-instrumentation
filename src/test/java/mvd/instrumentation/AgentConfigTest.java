package mvd.instrumentation;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AgentConfigTest {
    private static AgentConfig config(String... keyValues) {
        Map<String, String> props = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            props.put(keyValues[i], keyValues[i + 1]);
        }
        return AgentConfig.from(props::get);
    }

    @Test
    public void usesDefaultsWhenNothingIsSet() {
        AgentConfig config = config();

        assertEquals(50L, config.threshold);
        assertEquals("summary", config.format);
        assertEquals("stdout", config.output);
        assertEquals(100_000, config.maxEvents);
        assertTrue(config.reporter() instanceof ReportSummaryFormatter);
    }

    @Test
    public void readsThresholdProperty() {
        AgentConfig config = config(AgentConfig.THRESHOLD_PROPERTY, "120");

        assertEquals(120L, config.threshold);
    }

    @Test
    public void allowsZeroThreshold() {
        AgentConfig config = config(AgentConfig.THRESHOLD_PROPERTY, "0");

        assertEquals(0L, config.threshold);
    }

    @Test
    public void acceptsThresholdAtMaxLimit() {
        AgentConfig config = config(AgentConfig.THRESHOLD_PROPERTY, "180000");

        assertEquals(AgentConfig.MAX_THRESHOLD, config.threshold);
    }

    @Test
    public void capsThresholdExceedingMaxLimitAndLogsWarning() {
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errBytes));
        try {
            AgentConfig config = config(AgentConfig.THRESHOLD_PROPERTY, "86400000");

            assertEquals(AgentConfig.MAX_THRESHOLD, config.threshold);
        } finally {
            System.setErr(originalErr);
        }
        String err = errBytes.toString();
        assertTrue(err, err.contains("[WARN] " + AgentConfig.THRESHOLD_PROPERTY
                + " (86400000 ms) exceeds maximum limit of 180000 ms (3 minutes); capping to 180000 ms."));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeThreshold() {
        config(AgentConfig.THRESHOLD_PROPERTY, "-5");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonNumericThreshold() {
        config(AgentConfig.THRESHOLD_PROPERTY, "invalid_num");
    }

    @Test
    public void ignoresRemovedDurationProperties() {
        AgentConfig config = AgentConfig.from(property -> property.startsWith("duration.") ? "json" : null);

        assertEquals("summary", config.format);
    }

    @Test
    public void readsFormatOutputAndMaxEvents() {
        AgentConfig config = config(
                AgentConfig.FORMAT_PROPERTY, "json",
                AgentConfig.OUTPUT_PROPERTY, "/tmp/duration.jsonl",
                AgentConfig.MAX_EVENTS_PROPERTY, " 500 ");

        assertEquals("json", config.format);
        assertEquals("/tmp/duration.jsonl", config.output);
        assertEquals(500, config.maxEvents);
        assertTrue(config.reporter() instanceof ReportJsonFormatter);
    }

    @Test
    public void blankValuesFallBackToDefaults() {
        AgentConfig config = config(AgentConfig.FORMAT_PROPERTY, " ", AgentConfig.MAX_EVENTS_PROPERTY, "");

        assertEquals("summary", config.format);
        assertEquals(100_000, config.maxEvents);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownFormat() {
        config(AgentConfig.FORMAT_PROPERTY, "xml");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonNumericMaxEvents() {
        config(AgentConfig.MAX_EVENTS_PROPERTY, "lots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveMaxEvents() {
        config(AgentConfig.MAX_EVENTS_PROPERTY, "0");
    }

    @Test
    public void rejectsMaxEventsAboveIntRangeNamingTheRange() {
        assertMessage("mvd.instrumentation.agent.maxEvents must be between 1 and 2147483647: 2147483648",
                AgentConfig.MAX_EVENTS_PROPERTY, "2147483648");
    }

    @Test
    public void errorMessagesNameThePropertyAndValue() {
        assertMessage("mvd.instrumentation.agent.threshold must be at least 0: -5", AgentConfig.THRESHOLD_PROPERTY, "-5");
        assertMessage("mvd.instrumentation.agent.maxEvents must be an integer: lots", AgentConfig.MAX_EVENTS_PROPERTY, "lots");
    }

    private static void assertMessage(String expected, String property, String value) {
        try {
            config(property, value);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void handlesCaseInsensitiveFormatAndOutput() {
        AgentConfig config = config(
                AgentConfig.FORMAT_PROPERTY, "JSON",
                AgentConfig.OUTPUT_PROPERTY, "STDOUT");

        assertEquals("json", config.format);
        assertEquals("STDOUT", config.output);
        assertTrue(config.reporter() instanceof ReportJsonFormatter);
    }

    @Test
    public void createsParentDirectoriesForFileOutput() throws Exception {
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "duration-test-" + System.nanoTime());
        java.io.File file = new java.io.File(dir, "sub/report.txt");
        try {
            AgentConfig config = config(AgentConfig.OUTPUT_PROPERTY, file.getPath());
            try (java.io.PrintWriter out = config.openOutput()) {
                out.println("hello");
            }
            assertTrue(file.exists());
        } finally {
            file.delete();
            file.getParentFile().delete();
            dir.delete();
        }
    }
}
