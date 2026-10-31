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
        assertEquals(ReportFormat.TEXT, config.format);
        assertEquals(ReportOutput.STDOUT, config.output);
        assertEquals(100_000, config.maxEvents);
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
    public void rejectsThresholdAboveMaxLimit() {
        assertMessage("mvd.instrumentation.agent.threshold must be between 0 and 180000: 86400000",
                AgentConfig.THRESHOLD_PROPERTY, "86400000");
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

        assertEquals(ReportFormat.TEXT, config.format);
    }

    @Test
    public void readsFormatOutputAndMaxEvents() {
        AgentConfig config = config(
                AgentConfig.FORMAT_PROPERTY, "json",
                AgentConfig.OUTPUT_PROPERTY, "/tmp/duration.jsonl",
                AgentConfig.MAX_EVENTS_PROPERTY, " 500 ");

        assertEquals(ReportFormat.JSON, config.format);
        assertEquals(ReportOutput.Kind.FILE, config.output.kind);
        assertEquals(java.nio.file.Paths.get("/tmp/duration.jsonl"), config.output.file.get());
        assertEquals(500, config.maxEvents);
    }

    @Test
    public void readsHtmlFormat() {
        assertEquals(ReportFormat.HTML, config(AgentConfig.FORMAT_PROPERTY, "HTML").format);
    }

    @Test
    public void blankValuesFallBackToDefaults() {
        AgentConfig config = config(AgentConfig.FORMAT_PROPERTY, " ", AgentConfig.MAX_EVENTS_PROPERTY, "");

        assertEquals(ReportFormat.TEXT, config.format);
        assertEquals(100_000, config.maxEvents);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownFormat() {
        config(AgentConfig.FORMAT_PROPERTY, "xml");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRemovedSummaryFormat() {
        config(AgentConfig.FORMAT_PROPERTY, "summary");
    }

    @Test
    public void rejectsIllegalOutputPathNamingWhatIsAccepted() {
        assertMessage("mvd.instrumentation.report.output must be stdout, stderr or a file path: bad\u0000path",
                AgentConfig.OUTPUT_PROPERTY, "bad\u0000path");
    }

    @Test
    public void formatUsageListsEveryFormat() {
        assertEquals("text, json or html", ReportFormat.usage());
    }

    @Test
    public void standardStreamsHaveNoFile() {
        assertEquals(java.util.Optional.empty(), config().output.file);
        assertEquals(java.util.Optional.empty(), config(AgentConfig.OUTPUT_PROPERTY, "stderr").output.file);
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
        assertMessage("mvd.instrumentation.agent.threshold must be between 0 and 180000: -5", AgentConfig.THRESHOLD_PROPERTY, "-5");
        assertMessage("mvd.instrumentation.report.format must be text, json or html: xml", AgentConfig.FORMAT_PROPERTY, "xml");
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

        assertEquals(ReportFormat.JSON, config.format);
        assertEquals(ReportOutput.STDOUT, config.output);
    }
}
