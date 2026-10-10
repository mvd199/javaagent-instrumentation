package mvd.instrumentation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
}
