package mvd.instrumentation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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

    @Test(expected = IllegalArgumentException.class)
    public void globalWildcardIsRejected() {
        ClassFilter.fromPatterns("**");
    }

    @Test
    public void neverMatchesJdkOrJavassistClasses() {
        ClassFilter filter = ClassFilter.fromPatterns("java.util.**", "javax.*", "jdk.**", "sun.**",
                "com.sun.**", "javassist.**", "java.lang.String", "java/lang/*Builder");
        assertFalse(filter.test("java/util/IdentityHashMap$KeyIterator"));
        assertFalse(filter.test("java/lang/String"));
        assertFalse(filter.test("java/lang/StringBuilder"));
        assertFalse(filter.test("javax/Foo"));
        assertFalse(filter.test("jdk/internal/misc/Unsafe"));
        assertFalse(filter.test("sun/misc/Signal"));
        assertFalse(filter.test("com/sun/proxy/$Proxy1"));
        assertFalse(filter.test("javassist/CtClass"));
    }

    @Test
    public void neverMatchesAgentPackageOrItsSubpackages() {
        ClassFilter filter = ClassFilter.fromPatterns("mvd.**");
        assertFalse(filter.test("mvd/instrumentation/AgentLogger"));
        assertFalse(filter.test("mvd/instrumentation/DurationTransformer$LongStack"));
        assertFalse(filter.test("mvd/instrumentation/report/Anything"));
        assertTrue(filter.test("mvd/demo/DemoService"));
    }

    @Test
    public void doesNotExcludePackagesThatOnlyShareAPrefix() {
        ClassFilter filter = ClassFilter.fromPatterns("javafoo.**", "sunrise.*");
        assertTrue(filter.test("javafoo/Bar"));
        assertTrue(filter.test("sunrise/Bar"));
    }

    @Test
    public void warnsAboutPatternsCoveringExcludedClasses() {
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errBytes));
        try {
            ClassFilter.fromPatterns("java.util.**", "com.example.**");
        } finally {
            System.setErr(originalErr);
        }
        String err = errBytes.toString();
        assertTrue(err, err.contains("[WARN] pattern 'java.util.**' covers JDK, javassist or agent classes"));
        assertFalse(err, err.contains("com.example"));
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
    public void ignoresFullLineCommentsAndIndentedComments() throws Exception {
        ClassFilter filter = fromConfig(
                "# Full-line comment",
                "   # Indented comment",
                "com.example.Foo"
        );
        assertTrue(filter.test("com/example/Foo"));
        assertFalse(filter.test("# Full-line comment"));
    }

    @Test
    public void stripsInlineComments() throws Exception {
        ClassFilter filter = fromConfig("com.example.Foo # inline comment");
        assertTrue(filter.test("com/example/Foo"));
    }

    @Test
    public void ignoresBlankLinesAndTrimsWhitespace() throws Exception {
        ClassFilter filter = fromConfig(
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
    public void fromFileRejectsMissingFile() {
        ClassFilter.fromFile(new File(folder.getRoot(), "missing.cfg").getPath());
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

        ClassFilter filter = ClassFilter.fromFile(config.getPath());

        assertTrue(filter.test("com/example/Exact"));
        assertTrue(filter.test("com/example/service/OrderService"));
        assertFalse(filter.test("com/example/service/impl/OrderServiceImpl"));
        assertTrue(filter.test("com/example/repo/UserRepository"));
        assertTrue(filter.test("com/example/repo/jpa/JpaUserRepository"));
        assertFalse(filter.test("com/other/Foo"));
    }

    @Test
    public void parseLinesReturnsOnlyCleanedLines() {
        List<String> lines = ClassFilter.parseLines(Arrays.asList(
                "# header", "", "com.example.A", "  com.example.B # with comment", "", "# footer"));
        assertEquals(Arrays.asList("com.example.A", "com.example.B"), lines);
    }

    /** Cross-checks the glob semantics against the JDK's own glob matcher, which uses the same * and ** rules. */
    @Test
    public void matchesLikeJdkGlobMatcher() {
        assumeTrue("JDK glob matcher uses '/' only on POSIX file systems", "/".equals(FileSystems.getDefault().getSeparator()));
        List<String> globs = Arrays.asList("com/example/Foo", "com/example/*", "com/example/**", "*",
                "com/example/*Service", "com/**/Repo", "com/example/Foo$*", "com/*/web/*");
        List<String> classNames = Arrays.asList("Main", "com/example/Foo", "com/example/Foo$1", "com/example/FooBar",
                "com/example/OrderService", "com/example/sub/OrderService", "com/example/sub/deep/Baz",
                "com/example2/Foo", "com/a/Repo", "com/a/b/Repo", "com/a/web/Controller", "com/a/web/x/Controller");
        for (String glob : globs) {
            ClassFilter filter = ClassFilter.fromPatterns(glob);
            PathMatcher jdk = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            for (String className : classNames) {
                assertEquals(glob + " vs " + className, jdk.matches(Paths.get(className)), filter.test(className));
            }
        }
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

    private static ClassFilter fromConfig(String... lines) throws Exception {
        return new ClassFilter(ClassFilter.parseLines(Arrays.asList(lines)));
    }
}
