package mvd.instrumentation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassFilterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void exactMatchAcceptsTargetClass() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo");
        assertTrue(filter.test("com/example/Foo"));
    }

    @Test
    public void exactMatchConvertsDotNotationToInternalForm() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.service.OrderService");
        assertTrue(filter.test("com/example/service/OrderService"));
    }

    @Test
    public void exactMatchAcceptsSlashNotationDirectly() {
        ClassFilter filter = ClassFilter.fromPatterns("com/example/Foo");
        assertTrue(filter.test("com/example/Foo"));
    }

    @Test
    public void exactMatchRejectsDifferentClass() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo");
        assertFalse(filter.test("com/example/Bar"));
    }

    @Test
    public void exactMatchRejectsPrefixOverlap() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo");
        assertFalse(filter.test("com/example/FooBar"));
    }

    @Test
    public void exactMatchRejectsSubpackage() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo");
        assertFalse(filter.test("com/example/Foo/Bar"));
    }

    @Test
    public void exactMatchAcceptsInnerClassWhenExplicitlyListed() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo$Inner");
        assertTrue(filter.test("com/example/Foo$Inner"));
        assertFalse(filter.test("com/example/Foo"));
    }

    @Test
    public void singleLevelWildcardMatchesDirectClasses() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.*");
        assertTrue(filter.test("com/example/Foo"));
        assertTrue(filter.test("com/example/Bar"));
    }

    @Test
    public void singleLevelWildcardMatchesInnerClassesInPackage() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.*");
        assertTrue(filter.test("com/example/Foo$Inner"));
    }

    @Test
    public void singleLevelWildcardRejectsSubpackages() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.*");
        assertFalse(filter.test("com/example/sub/Baz"));
        assertFalse(filter.test("com/example/sub/deep/Qux"));
    }

    @Test
    public void singleLevelWildcardRejectsSiblingPackages() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.*");
        assertFalse(filter.test("com/example2/Foo"));
        assertFalse(filter.test("com/examples/Foo"));
        assertFalse(filter.test("org/example/Foo"));
    }

    @Test
    public void singleLevelWildcardRejectsPackageItselfAsClass() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.*");
        assertFalse(filter.test("com/example"));
    }

    @Test
    public void defaultPackageSingleLevelWildcardMatchesRootClassesOnly() {
        ClassFilter filter = ClassFilter.fromPatterns("*");
        assertTrue(filter.test("Main"));
        assertTrue(filter.test("AppHelper"));
        assertFalse(filter.test("com/example/Main"));
    }

    @Test
    public void recursiveWildcardMatchesDirectAndNestedSubpackages() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.**");
        assertTrue(filter.test("com/example/Foo"));
        assertTrue(filter.test("com/example/sub/Bar"));
        assertTrue(filter.test("com/example/sub/deep/Baz"));
    }

    @Test
    public void recursiveWildcardRejectsSiblingPackages() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.**");
        assertFalse(filter.test("com/example2/Foo"));
        assertFalse(filter.test("com/examples/Bar"));
        assertFalse(filter.test("org/example/Foo"));
    }

    @Test
    public void globalWildcardMatchesAnyClass() {
        ClassFilter filter = ClassFilter.fromPatterns("**");
        assertTrue(filter.test("Main"));
        assertTrue(filter.test("com/example/Foo"));
        assertTrue(filter.test("java/lang/String"));
    }

    @Test
    public void arbitraryGlobMatchesPatternWithinPackage() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.*Service");
        assertTrue(filter.test("com/example/OrderService"));
        assertTrue(filter.test("com/example/UserService"));
        assertFalse(filter.test("com/example/OrderDao"));
        assertFalse(filter.test("com/example/sub/OrderService"));
    }

    @Test
    public void arbitraryGlobEscapesSpecialRegexCharacters() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo$*");
        assertTrue(filter.test("com/example/Foo$1"));
        assertTrue(filter.test("com/example/Foo$Inner"));
        assertFalse(filter.test("com/example/Bar$1"));
    }

    @Test
    public void ignoresFullLineCommentsAndIndentedComments() {
        ClassFilter filter = ClassFilter.fromPatterns(
                "# Full-line comment",
                "   # Indented comment",
                "com.example.Foo"
        );
        assertTrue(filter.test("com/example/Foo"));
        assertFalse(filter.test("# Full-line comment"));
    }

    @Test
    public void stripsInlineComments() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.Foo # inline comment");
        assertTrue(filter.test("com/example/Foo"));
    }

    @Test
    public void ignoresBlankLinesAndTrimsWhitespace() {
        ClassFilter filter = ClassFilter.fromPatterns(
                "",
                "   ",
                "\t\t",
                "  com.example.Foo  ",
                " \t com.example.sub.* \t "
        );
        assertTrue(filter.test("com/example/Foo"));
        assertTrue(filter.test("com/example/sub/Bar"));
        assertFalse(filter.test("com/example/sub/deep/Baz"));
    }

    @Test
    public void nullClassNameReturnsFalseSafely() {
        ClassFilter filter = ClassFilter.fromPatterns("com.example.**", "com.other.*", "com.Single");
        assertFalse(filter.test(null));
    }

    @Test
    public void emptyFilterRejectsAllClasses() {
        ClassFilter filter = ClassFilter.fromPatterns();
        assertFalse(filter.test("com/example/Foo"));
        assertFalse(filter.test("Main"));
        assertFalse(filter.test(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFileRejectsNullPath() {
        ClassFilter.fromFile((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFileRejectsEmptyPath() {
        ClassFilter.fromFile("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFileRejectsNullFile() {
        ClassFilter.fromFile((File) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFileRejectsMissingFile() {
        ClassFilter.fromFile(new File(folder.getRoot(), "missing.cfg"));
    }

    @Test
    public void fromFileLoadsConfigurationFileSuccessfully() throws Exception {
        File config = folder.newFile("test.cfg");
        Files.write(config.toPath(), Arrays.asList(
                "# Header comment",
                "com.example.Exact",
                "",
                "# Services",
                "com.example.service.*",
                "",
                "com.example.repo.** # repository hierarchy"
        ));

        ClassFilter filter = ClassFilter.fromFile(config);

        assertTrue(filter.test("com/example/Exact"));
        assertTrue(filter.test("com/example/service/OrderService"));
        assertFalse(filter.test("com/example/service/impl/OrderServiceImpl"));
        assertTrue(filter.test("com/example/repo/UserRepository"));
        assertTrue(filter.test("com/example/repo/jpa/JpaUserRepository"));
        assertFalse(filter.test("com/other/Foo"));
    }

    @Test
    public void parseLinesReturnsOnlyCleanedLines() throws Exception {
        String content = "# header\n\ncom.example.A\n  com.example.B # with comment\n\n# footer\n";
        List<String> lines = ClassFilter.parseLines(new StringReader(content));
        assertEquals(Arrays.asList("com.example.A", "com.example.B"), lines);
    }

    @Test
    public void multipleRulesCombineUnionLogic() {
        ClassFilter filter = ClassFilter.fromPatterns(
                "com.example.specific.Target",
                "com.example.web.*",
                "com.example.core.**"
        );

        assertTrue(filter.test("com/example/specific/Target"));
        assertTrue(filter.test("com/example/web/Controller"));
        assertFalse(filter.test("com/example/web/internal/Helper"));
        assertTrue(filter.test("com/example/core/Engine"));
        assertTrue(filter.test("com/example/core/deep/nested/Module"));
        assertFalse(filter.test("com/unrelated/Class"));
    }

    @Test
    public void evaluationPerformanceSanityCheck() {
        ClassFilter filter = ClassFilter.fromPatterns(
                "com.example.service.OrderService",
                "com.example.service.UserService",
                "com.example.web.*",
                "com.example.core.**"
        );
        String[] samples = {
                "com/example/service/OrderService",
                "com/example/web/LoginController",
                "com/example/web/sub/Failed",
                "com/example/core/deep/nested/Engine",
                "java/lang/String",
                "java/util/ArrayList",
                "com/example2/service/OrderService"
        };
        // Warmup loop to trigger JIT compilation
        int sink = 0;
        for (int i = 0; i < 50_000; i++) {
            for (String sample : samples) {
                sink += filter.test(sample) ? 1 : 0;
            }
        }
        // Timed measurement loop: 100,000 passes * 7 samples = 700,000 evaluations
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            for (String sample : samples) {
                sink += filter.test(sample) ? 1 : 0;
            }
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("Sink should be positive: " + sink, sink > 0);
        assertTrue("700,000 evaluations should complete in under 500ms, took " + durationMs + "ms", durationMs < 500L);
    }
}
