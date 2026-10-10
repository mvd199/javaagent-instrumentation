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
}
