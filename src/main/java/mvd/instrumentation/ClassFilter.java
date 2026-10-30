package mvd.instrumentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.ClassPool;

/**
 * Decides whether a class should be instrumented. Class names are tested in JVM internal form
 * ({@code com/example/Foo}); patterns may use dots or slashes.
 * <p>
 * Each pattern is a glob where {@code *} matches within one package and {@code **} also crosses packages:
 * <ul>
 *   <li>Exact match: {@code com.example.Foo}</li>
 *   <li>Single-level package wildcard: {@code com.example.*} (matches direct classes in package)</li>
 *   <li>Recursive prefix wildcard: {@code com.example.**} (matches package and all subpackages)</li>
 *   <li>Other globs: e.g. {@code com.example.*Service}</li>
 * </ul>
 * Exact names and the two package wildcards are matched without regex; other globs share one compiled regex.
 * <p>
 * JDK classes, javassist and the agent's own classes are never matched:
 * <ul>
 *   <li>JDK classes are loaded by the bootstrap or platform loader, which cannot see the agent classes that the
 *       injected code calls, so they would throw {@code NoClassDefFoundError} and can abort JVM startup.</li>
 *   <li>Agent classes would call themselves recursively from the injected code, and the shutdown hook that
 *       writes the report would fail.</li>
 *   <li>javassist runs inside the transformer, so instrumenting it would transform the code doing the
 *       transforming.</li>
 * </ul>
 */
final class ClassFilter implements Predicate<String> {
    /** Internal-name prefixes that are never instrumented, including the agent's own package and its subpackages. */
    private static final String[] EXCLUDED_PREFIXES = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/", internalPackage(ClassPool.class), internalPackage(DurationAgent.class)};
    private static final Pattern WILDCARD = Pattern.compile("\\*\\*|\\*");

    private final Set<String> exactMatches = new HashSet<>();
    /** {@code com/example/**} is stored as {@code com/example/}. */
    private final List<String> recursivePrefixes = new ArrayList<>();
    /** {@code com/example/*} is stored as {@code com/example/}. */
    private final List<String> singleLevelPrefixes = new ArrayList<>();
    /** Any other glob; null when there are none. */
    private final Pattern otherGlobs;

    /** {@code patterns} must already be trimmed and free of comments, as returned by {@link #parseLines}. */
    ClassFilter(Collection<String> patterns) {
        List<String> regexes = new ArrayList<>();
        for (String raw : patterns) {
            // Normalize dots to slashes for JVM internal class name format
            String glob = raw.replace('.', '/');
            if ("**".equals(glob)) {
                throw new IllegalArgumentException("'**' would instrument every library class;"
                        + " list your own packages instead, e.g. com.example.**");
            }
            if (isExcluded(glob)) {
                AgentLogger.LOG.warn("pattern '" + raw + "' covers JDK, javassist or agent classes, which are never instrumented");
            }
            // The common shapes skip the regex: a hash lookup or prefix check is ~50x faster.
            int firstStar = glob.indexOf('*');
            if (firstStar == -1) {
                exactMatches.add(glob);
            } else if (glob.endsWith("/**") && firstStar == glob.length() - 2) {
                recursivePrefixes.add(glob.substring(0, firstStar));
            } else if (glob.endsWith("/*") && firstStar == glob.length() - 1) {
                singleLevelPrefixes.add(glob.substring(0, firstStar));
            } else {
                regexes.add("(?:" + globToRegex(glob) + ")");
            }
        }
        this.otherGlobs = regexes.isEmpty() ? null : Pattern.compile(String.join("|", regexes));
    }

    static ClassFilter fromPatterns(String... patterns) {
        return new ClassFilter(Arrays.asList(patterns));
    }

    static ClassFilter fromFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("Configuration file path is missing; pass it as -javaagent:<agent.jar>=<classes.cfg>");
        }
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Configuration file not found: " + filePath);
        }
        try {
            return new ClassFilter(parseLines(Files.readAllLines(path, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read configuration: " + filePath, e);
        }
    }

    /** Strips {@code #} comments and whitespace, and drops lines left empty. */
    static List<String> parseLines(List<String> lines) {
        List<String> patterns = new ArrayList<>();
        for (String line : lines) {
            int commentIdx = line.indexOf('#');
            if (commentIdx >= 0) {
                line = line.substring(0, commentIdx);
            }
            line = line.trim();
            if (!line.isEmpty()) {
                patterns.add(line);
            }
        }
        return patterns;
    }

    @Override
    public boolean test(String className) {
        if (className == null || isExcluded(className)) {
            return false;
        }
        if (exactMatches.contains(className)) {
            return true;
        }
        for (String prefix : recursivePrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : singleLevelPrefixes) {
            if (className.startsWith(prefix) && className.indexOf('/', prefix.length()) == -1) {
                return true;
            }
        }
        return otherGlobs != null && otherGlobs.matcher(className).matches();
    }

    /** Also applied to patterns, to warn about entries that can never match. */
    static boolean isExcluded(String internalName) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** {@code **} becomes {@code .*}, {@code *} becomes {@code [^/]*}, and everything else is matched literally. */
    static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        Matcher m = WILDCARD.matcher(glob);
        int literalStart = 0;
        while (m.find()) {
            regex.append(Pattern.quote(glob.substring(literalStart, m.start())));
            regex.append(m.group().length() == 2 ? ".*" : "[^/]*");
            literalStart = m.end();
        }
        regex.append(Pattern.quote(glob.substring(literalStart)));
        return regex.toString();
    }

    /** {@code mvd.instrumentation.DurationAgent} -> {@code mvd/instrumentation/}, so a package rename cannot break it. */
    private static String internalPackage(Class<?> type) {
        String name = type.getName();
        return name.substring(0, name.lastIndexOf('.') + 1).replace('.', '/');
    }
}
