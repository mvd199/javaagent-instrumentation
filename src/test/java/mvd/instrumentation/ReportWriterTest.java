package mvd.instrumentation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReportWriterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static AgentConfig jsonConfig(File output, String maxEvents) {
        return AgentConfig.from(property -> {
            switch (property) {
                case AgentConfig.FORMAT_PROPERTY: return "json";
                case AgentConfig.OUTPUT_PROPERTY: return output.getPath();
                case AgentConfig.MAX_EVENTS_PROPERTY: return maxEvents;
                default: return null;
            }
        });
    }

    @Test
    public void writesConfiguredFormatToConfiguredFile() throws Exception {
        File output = new File(folder.getRoot(), "report.jsonl");
        CoreRecorder recorder = new CoreRecorder(0, 10);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L, 0);

        new ReportWriter(jsonConfig(output, null)).write(recorder.snapshot());

        String content = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        assertTrue(content, content.contains("\"method\":\"a.B.slow()\""));
        assertEquals(2, content.split("\\R").length);
    }

    @Test
    public void writesHtmlFormatToConfiguredFile() throws Exception {
        File output = new File(folder.getRoot(), "report.html");
        CoreRecorder recorder = new CoreRecorder(0, 10);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L, 0);
        AgentConfig config = AgentConfig.from(property -> {
            switch (property) {
                case AgentConfig.FORMAT_PROPERTY: return "html";
                case AgentConfig.OUTPUT_PROPERTY: return output.getPath();
                default: return null;
            }
        });

        new ReportWriter(config).write(recorder.snapshot());

        String content = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        assertTrue(content, content.startsWith("<!DOCTYPE html>"));
        assertTrue(content, content.contains("\"method\":\"a.B.slow()\""));
    }

    @Test
    public void jsonMetaLineCarriesDroppedCalls() throws Exception {
        File output = new File(folder.getRoot(), "report.jsonl");
        CoreRecorder recorder = new CoreRecorder(0, 1);
        recorder.record("a.B.first()", System.nanoTime() - 20_000_000L, 0);
        recorder.record("a.B.second()", System.nanoTime() - 20_000_000L, 0);

        new ReportWriter(jsonConfig(output, "1")).write(recorder.snapshot());

        String content = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        assertTrue(content, content.startsWith("{\"type\":\"meta\",\"thresholdMs\":0,\"dropped\":1}\n"));
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
        CoreRecorder recorder = new CoreRecorder(0, 10);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L, 0);

        assertEquals(false, ReportWriter.writeTo(new java.io.PrintWriter(failing), new ReportJsonFormatter(), recorder.snapshot()));
    }

    @Test
    public void writeToReportsTrueWhenWritingSucceeds() {
        java.io.StringWriter ok = new java.io.StringWriter();

        assertEquals(true, ReportWriter.writeTo(new java.io.PrintWriter(ok), new ReportJsonFormatter(),
                new Report(java.util.Collections.<ReportRecord>emptyList(), 0, 50)));
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
        CoreRecorder recorder = new CoreRecorder(0, 10);
        recorder.record("a.B.slow()", System.nanoTime() - 20_000_000L, 0);

        assertEquals(false, ReportWriter.writeTo(new java.io.PrintWriter(failingFlush), new ReportJsonFormatter(), recorder.snapshot()));
    }

    @Test
    public void createsParentDirectoriesForFileOutput() throws Exception {
        File file = new File(folder.getRoot(), "sub/dir/report.txt");

        try (java.io.PrintWriter out = ReportWriter.open(ReportOutput.parse(file.getPath()).get())) {
            out.println("hello");
        }

        assertTrue(file.exists());
    }
}
