package mvd.instrumentation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DurationAgentTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test(expected = IllegalArgumentException.class)
    public void whitelistRejectsNullArgs() {
        DurationAgent.createWhitelist(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void whitelistRejectsEmptyArgs() {
        DurationAgent.createWhitelist("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void whitelistRejectsMissingFile() {
        DurationAgent.createWhitelist(new File(folder.getRoot(), "missing.cfg").getPath());
    }

    @Test
    public void whitelistConvertsClassNamesToInternalForm() throws Exception {
        File config = folder.newFile("classes.cfg");
        Files.write(config.toPath(), Arrays.asList("com.example.Foo", "com.example.sub.Bar"));

        Set<String> whitelist = DurationAgent.createWhitelist(config.getPath());

        assertEquals(new HashSet<>(Arrays.asList("com/example/Foo", "com/example/sub/Bar")), whitelist);
    }

    @Test
    public void whitelistIgnoresDuplicates() throws Exception {
        File config = folder.newFile("classes.cfg");
        Files.write(config.toPath(), Arrays.asList("com.example.Foo", "com.example.Foo"));

        assertEquals(1, DurationAgent.createWhitelist(config.getPath()).size());
    }

    @Test
    public void whitelistIgnoresCommentsAndBlankLines() throws Exception {
        File config = folder.newFile("classes.cfg");
        Files.write(config.toPath(), Arrays.asList(
                "# Header comment",
                "  ",
                "com.example.Foo",
                "   # indented comment",
                "com.example.sub.Bar # inline comment",
                ""
        ));

        Set<String> whitelist = DurationAgent.createWhitelist(config.getPath());

        assertEquals(new HashSet<>(Arrays.asList("com/example/Foo", "com/example/sub/Bar")), whitelist);
    }

    @Test
    public void createClassFilterBuildsFunctionalFilterFromFile() throws Exception {
        File config = folder.newFile("classes.cfg");
        Files.write(config.toPath(), Arrays.asList(
                "# Whitelist configuration",
                "com.example.service.*",
                "com.example.repo.**"
        ));

        ClassFilter filter = DurationAgent.createClassFilter(config.getPath());

        assertTrue(filter.test("com/example/service/OrderService"));
        assertTrue(filter.test("com/example/repo/jpa/UserRepo"));
        assertEquals(false, filter.test("com/other/Foo"));
    }

    @Test
    public void writeReportWritesConfiguredFormatToConfiguredFile() throws Exception {
        File output = new File(folder.getRoot(), "report.jsonl");
        ReportConfig config = ReportConfig.from(property -> {
            switch (property) {
                case ReportConfig.FORMAT_PROPERTY: return "json";
                case ReportConfig.OUTPUT_PROPERTY: return output.getPath();
                default: return null;
            }
        });
        Recorder recorder = new Recorder(0, 10, () -> 0);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L);

        DurationAgent.writeReport(recorder, config);

        String content = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        assertTrue(content, content.contains("\"method\":\"a.B.slow()\""));
        assertEquals(1, content.split("\\R").length);
    }

    @Test
    public void writeToReportsFalseWhenTheUnderlyingWriterFails() {
        java.io.Writer failing = new java.io.Writer() {
            @Override public void write(char[] buf, int off, int len) throws java.io.IOException {
                throw new java.io.IOException("disk full");
            }
            @Override public void flush() throws java.io.IOException {
                throw new java.io.IOException("disk full");
            }
            @Override public void close() { }
        };
        Recorder recorder = new Recorder(0, 10, () -> 0);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L);

        assertEquals(false, DurationAgent.writeTo(new java.io.PrintWriter(failing), new JsonReporter(), recorder.snapshot()));
    }

    @Test
    public void writeToReportsTrueWhenWritingSucceeds() {
        java.io.StringWriter ok = new java.io.StringWriter();

        assertEquals(true, DurationAgent.writeTo(new java.io.PrintWriter(ok), new JsonReporter(),
                new Report(java.util.Collections.<CallRecord>emptyList(), 0, 50)));
    }

    @Test
    public void writeToReportsFalseWhenFlushFails() {
        java.io.Writer failingFlush = new java.io.Writer() {
            @Override public void write(char[] buf, int off, int len) { }
            @Override public void flush() throws java.io.IOException {
                throw new java.io.IOException("flush failed");
            }
            @Override public void close() { }
        };
        Recorder recorder = new Recorder(0, 10, () -> 0);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L);

        assertEquals(false, DurationAgent.writeTo(new java.io.PrintWriter(failingFlush), new JsonReporter(), recorder.snapshot()));
    }

    @Test
    public void writeReportPrintsDroppedCallsToStderrInJsonFormat() throws Exception {
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errBytes));
        try {
            File output = new File(folder.getRoot(), "report.jsonl");
            ReportConfig config = ReportConfig.from(property -> {
                switch (property) {
                    case ReportConfig.FORMAT_PROPERTY: return "json";
                    case ReportConfig.OUTPUT_PROPERTY: return output.getPath();
                    case ReportConfig.MAX_EVENTS_PROPERTY: return "1";
                    default: return null;
                }
            });
            Recorder recorder = new Recorder(0, 1, () -> 0);
            recorder.record("a.B.first()", System.nanoTime() - 20_000_000L);
            recorder.record("a.B.second()", System.nanoTime() - 20_000_000L);

            DurationAgent.writeReport(recorder, config);

            String errOutput = errBytes.toString();
            assertTrue(errOutput, errOutput.contains("DurationAgent:"));
            assertTrue(errOutput, errOutput.contains("[WARN]"));
            assertTrue(errOutput, errOutput.contains("dropped 1 calls exceeding maxEvents (1)"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    public void recorderFiltersInvocationsAccordingToConfiguredThreshold() {
        ReportConfig customConfig = ReportConfig.from(property -> {
            if (ReportConfig.THRESHOLD_PROPERTY.equals(property)) {
                return "20";
            }
            return null;
        });
        assertEquals(20L, customConfig.threshold);

        Recorder customRecorder = new Recorder(customConfig.threshold, 10, () -> 0);
        // Call taking 10ms: under 20ms threshold, should not be recorded
        customRecorder.record("a.B.fast()", System.nanoTime() - 10_000_000L);
        assertEquals(0, customRecorder.snapshot().records.size());

        // Call taking 30ms: over 20ms threshold, should be recorded
        customRecorder.record("a.B.medium()", System.nanoTime() - 30_000_000L);
        assertEquals(1, customRecorder.snapshot().records.size());
        assertEquals("a.B.medium()", customRecorder.snapshot().records.get(0).method);

        // Same 30ms call with default 50ms threshold should not be recorded
        ReportConfig defaultConfig = ReportConfig.from(property -> null);
        assertEquals(50L, defaultConfig.threshold);
        Recorder defaultRecorder = new Recorder(defaultConfig.threshold, 10, () -> 0);
        defaultRecorder.record("a.B.medium()", System.nanoTime() - 30_000_000L);
        assertEquals(0, defaultRecorder.snapshot().records.size());
    }
}
