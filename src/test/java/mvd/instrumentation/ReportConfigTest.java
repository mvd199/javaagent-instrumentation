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

        assertEquals("summary", config.format);
        assertEquals("stdout", config.output);
        assertEquals(100_000, config.maxEvents);
        assertTrue(config.reporter() instanceof SummaryReporter);
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
}
