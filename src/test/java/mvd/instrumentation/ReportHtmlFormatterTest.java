package mvd.instrumentation;

import org.junit.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReportHtmlFormatterTest {
    private static String render(Report report) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        new ReportHtmlFormatter().write(report, out);
        out.flush();
        return text.toString();
    }

    private static ScriptEngine runScript(Report report, String filterQuery) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("rawData", ReportHtmlFormatter.data(report));
        engine.put("filterQuery", filterQuery);
        engine.eval(
                "function makeEl(tag) {\n"
                + "  var text = '';\n"
                + "  var el = {\n"
                + "    tag: tag, className: '', value: '', style: {}, children: [], listeners: {},\n"
                + "    appendChild: function(c) { this.children.push(c); return c; },\n"
                + "    addEventListener: function(ev, fn) { this.listeners[ev] = fn; },\n"
                + "    findAll: function(cls) {\n"
                + "      var out = [];\n"
                + "      if ((' ' + this.className + ' ').indexOf(' ' + cls + ' ') >= 0) out.push(this);\n"
                + "      for (var i = 0; i < this.children.length; i++) out = out.concat(this.children[i].findAll(cls));\n"
                + "      return out;\n"
                + "    }\n"
                + "  };\n"
                + "  Object.defineProperty(el, 'textContent', {\n"
                + "    get: function() { return text; },\n"
                + "    set: function(v) { text = String(v); this.children = []; }\n"
                + "  });\n"
                + "  return el;\n"
                + "}\n"
                + "var app = makeEl('div');\n"
                + "var dataEl = { textContent: String(rawData) };\n"
                + "var filterInput = null;\n"
                + "var document = {\n"
                + "  getElementById: function(id) { return id === 'app' ? app : dataEl; },\n"
                + "  createElement: function(tag) { var e = makeEl(tag); if (tag === 'input') filterInput = e; return e; }\n"
                + "};\n"
                + "function Option(text, value) { var o = makeEl('option'); o.textContent = text; o.value = value; return o; }\n");
        try (InputStream in = ReportHtmlFormatter.class.getResourceAsStream("/mvd/instrumentation/report.js")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            for (int n; (n = in.read(chunk)) > 0; ) {
                buf.write(chunk, 0, n);
            }
            engine.eval(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        }
        if (filterQuery != null) {
            engine.eval("filterInput.value = String(filterQuery); filterInput.listeners['input']();");
        }
        return engine;
    }

    @Test
    public void embedsDataScriptAndStylesheet() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1000L, "main", "a.B.m()", 60, 0));

        String html = render(new Report(records, 3, 50));

        assertTrue(html, html.contains("{\"type\":\"meta\",\"thresholdMs\":50,\"dropped\":3}\n"
                + "{\"type\":\"call\",\"timestamp\":1000,\"thread\":\"main\",\"method\":\"a.B.m()\",\"durationMs\":60,\"depth\":0}\n"));
        assertTrue(html.contains("getElementById('report-data')"));
        assertTrue(html.contains("--accent"));
        assertFalse(html.contains("{{"));
    }

    @Test
    public void dataCannotCloseTheScriptElement() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "</script><b>", "a.B.<init>()", 60, 0));

        String html = render(new Report(records, 0, 50));

        assertTrue(html.contains("\\u003c/script>\\u003cb>"));
        assertTrue(html.contains("a.B.\\u003cinit>()"));
        assertEquals(2, html.split("</script>", -1).length - 1);
    }

    @Test
    public void placeholderTextInDataIsNotExpanded() {
        List<ReportRecord> records = Arrays.asList(new ReportRecord(1L, 1L, "t", "{{STYLE}}", 60, 0));

        String html = render(new Report(records, 0, 50));

        assertTrue(html.contains("\"method\":\"{{STYLE}}\""));
    }

    @Test
    public void filteringDoesNotNestChildUnderUnrelatedEarlierRoot() throws Exception {
        List<ReportRecord> records = Arrays.asList(
                new ReportRecord(100_000_000L, 1000L, "main", "a.Match.firstRoot()", 50, 0),
                new ReportRecord(200_000_000L, 1100L, "main", "a.Other.secondRoot()", 60, 0),
                new ReportRecord(210_000_000L, 1110L, "main", "a.Match.childOfSecond()", 40, 1));

        ScriptEngine engine = runScript(new Report(records, 0, 50), "Match");

        Number rootNodes = (Number) engine.eval("app.findAll('tree')[0].children.length");
        assertEquals(2, rootNodes.intValue());
    }

    @Test
    public void droppedParentDoesNotNestChildUnderFinishedEarlierRoot() throws Exception {
        List<ReportRecord> records = Arrays.asList(
                new ReportRecord(100_000_000L, 1000L, "main", "a.B.finishedRoot()", 50, 0),
                new ReportRecord(210_000_000L, 1110L, "main", "a.B.childOfDroppedSecondRoot()", 40, 1));

        ScriptEngine engine = runScript(new Report(records, 1, 50), null);

        Number rootNodes = (Number) engine.eval("app.findAll('tree')[0].children.length");
        assertEquals(2, rootNodes.intValue());
    }

    @Test
    public void zeroDurationCallHasValidBarWidthAndIsNotMarkedSlow() throws Exception {
        List<ReportRecord> records = Arrays.asList(
                new ReportRecord(100_000_000L, 1000L, "main", "a.B.instant()", 0, 0));

        ScriptEngine engine = runScript(new Report(records, 0, 0), null);

        String callClass = (String) engine.eval("app.findAll('call')[0].className");
        String barWidth = (String) engine.eval("app.findAll('fill')[0].style.width");
        assertEquals("call", callClass);
        assertFalse("Expected valid CSS width, got: " + barWidth, barWidth.contains("NaN"));
    }
}