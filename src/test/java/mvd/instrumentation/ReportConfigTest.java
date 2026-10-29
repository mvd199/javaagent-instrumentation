package mvd.instrumentation;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReportConfigTest {
    private static ReportConfig config(String... keyValues) {
        Map<String, String> props = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            props.put(keyValues[i], keyValues[i + 1]);
        }
        return ReportConfig.from(props::get);
    }

    @Test
    public void usesDefaultsWhenNothingIsSet() {
        ReportConfig config = config();

        assertEquals(50L, config.threshold);
        assertEquals("summary", config.format);
        assertEquals("stdout", config.output);
        assertEquals(100_000, config.maxEvents);
        assertTrue(config.reporter() instanceof SummaryReporter);
    }

    @Test
    public void readsThresholdProperty() {
        ReportConfig config = config(ReportConfig.THRESHOLD_PROPERTY, "120");

        assertEquals(120L, config.threshold);
    }

    @Test
    public void allowsZeroThreshold() {
        ReportConfig config = config(ReportConfig.THRESHOLD_PROPERTY, "0");

        assertEquals(0L, config.threshold);
    }

    @Test
    public void capsThresholdExceedingMaxLimitAndLogsWarning() {
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errBytes));
        try {
            ReportConfig config = config(ReportConfig.THRESHOLD_PROPERTY, "200000");

            assertEquals(ReportConfig.MAX_THRESHOLD, config.threshold);
            String err = errBytes.toString();
            assertTrue(err, err.contains("DurationAgent:"));
            assertTrue(err, err.contains("[WARN]"));
            assertTrue(err, err.contains(ReportConfig.THRESHOLD_PROPERTY + " (200000 ms) exceeds maximum limit of 180000 ms (3 minutes); capping to 180000 ms."));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    public void fallsBackToDefaultOnNegativeThresholdAndLogsWarning() {
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errBytes));
        try {
            ReportConfig config = config(ReportConfig.THRESHOLD_PROPERTY, "-5");

            assertEquals(ReportConfig.DEFAULT_THRESHOLD, config.threshold);
            String err = errBytes.toString();
            assertTrue(err, err.contains("DurationAgent:"));
            assertTrue(err, err.contains("[WARN]"));
            assertTrue(err, err.contains("invalid " + ReportConfig.THRESHOLD_PROPERTY + " '-5'; falling back to default (50 ms)."));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    public void fallsBackToDefaultOnNonNumericThresholdAndLogsWarning() {
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errBytes));
        try {
            ReportConfig config = config(ReportConfig.THRESHOLD_PROPERTY, "invalid_num");

            assertEquals(ReportConfig.DEFAULT_THRESHOLD, config.threshold);
            String err = errBytes.toString();
            assertTrue(err, err.contains("DurationAgent:"));
            assertTrue(err, err.contains("[WARN]"));
            assertTrue(err, err.contains("invalid " + ReportConfig.THRESHOLD_PROPERTY + " 'invalid_num'; falling back to default (50 ms)."));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    public void readsFormatOutputAndMaxEvents() {
        ReportConfig config = config(
                ReportConfig.FORMAT_PROPERTY, "json",
                ReportConfig.OUTPUT_PROPERTY, "/tmp/duration.jsonl",
                ReportConfig.MAX_EVENTS_PROPERTY, " 500 ");

        assertEquals("json", config.format);
        assertEquals("/tmp/duration.jsonl", config.output);
        assertEquals(500, config.maxEvents);
        assertTrue(config.reporter() instanceof JsonReporter);
    }

    @Test
    public void blankValuesFallBackToDefaults() {
        ReportConfig config = config(ReportConfig.FORMAT_PROPERTY, " ", ReportConfig.MAX_EVENTS_PROPERTY, "");

        assertEquals("summary", config.format);
        assertEquals(100_000, config.maxEvents);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownFormat() {
        config(ReportConfig.FORMAT_PROPERTY, "xml");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonNumericMaxEvents() {
        config(ReportConfig.MAX_EVENTS_PROPERTY, "lots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveMaxEvents() {
        config(ReportConfig.MAX_EVENTS_PROPERTY, "0");
    }

    @Test
    public void handlesCaseInsensitiveFormatAndOutput() {
        ReportConfig config = config(
                ReportConfig.FORMAT_PROPERTY, "JSON",
                ReportConfig.OUTPUT_PROPERTY, "STDOUT");

        assertEquals("json", config.format);
        assertEquals("STDOUT", config.output);
        assertTrue(config.reporter() instanceof JsonReporter);
    }

    @Test
    public void createsParentDirectoriesForFileOutput() throws Exception {
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "duration-test-" + System.nanoTime());
        java.io.File file = new java.io.File(dir, "sub/report.txt");
        try {
            ReportConfig config = config(ReportConfig.OUTPUT_PROPERTY, file.getPath());
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
