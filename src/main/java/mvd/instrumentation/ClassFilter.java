package mvd.instrumentation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Decides whether a class should be instrumented. Class names are tested in JVM internal form
 * ({@code com/example/Foo}); patterns may use dots or slashes.
 * <p>
 * Supports:
 * <ul>
 *   <li>Exact match: {@code com.example.Foo}</li>
 *   <li>Single-level package wildcard: {@code com.example.*} (matches direct classes in package)</li>
 *   <li>Recursive prefix wildcard: {@code com.example.**} (matches package and all subpackages)</li>
 *   <li>Default package single-level: {@code *}</li>
 *   <li>Other globs: e.g. {@code com.example.*Service}</li>
 * </ul>
 * JDK classes, javassist and the agent's own classes are never matched: the instrumentation calls into
 * agent classes that those classes cannot see, which aborts JVM startup or kills the shutdown hook that
 * writes the report. The bare {@code **} pattern is rejected for the same reason.
 */
final class ClassFilter implements Predicate<String> {
    /** Internal-name prefixes that are never instrumented. */
    private static final String[] EXCLUDED_PREFIXES = {"java/", "javax/", "jdk/", "sun/", "com/sun/", "javassist/"};
    /** Agent classes live directly in this package; subpackages (e.g. the demo) may be instrumented. */
    private static final String AGENT_PACKAGE = "mvd/instrumentation/";

    private final boolean matchDefaultPackage;
    private final Set<String> exactMatches;
    private final String[] recursivePrefixes;
    private final String[] singleLevelPrefixes;
    private final Pattern[] globPatterns;

    /** {@code patterns} must already be trimmed and free of comments, as returned by {@link #parseLines}. */
    ClassFilter(Collection<String> patterns) {
        boolean defaultPkg = false;
        Set<String> exact = new HashSet<>();
        List<String> recursive = new ArrayList<>();
        List<String> single = new ArrayList<>();
        List<Pattern> globs = new ArrayList<>();

        for (String raw : patterns) {
            // Normalize dots to slashes for JVM internal class name format
            String pattern = raw.replace('.', '/');

            if ("**".equals(pattern)) {
                throw new IllegalArgumentException("'**' would instrument JDK and agent classes;"
                        + " list your own packages instead, e.g. com.example.**");
            }
            if (isExcluded(pattern)) {
                AgentLogger.warn("pattern '" + raw + "' covers JDK, javassist or agent classes, which are never instrumented");
            }

            if ("*".equals(pattern)) {
                defaultPkg = true;
            } else if (pattern.endsWith("/**") && pattern.indexOf('*') == pattern.length() - 2) {
                // e.g. "com/example/**" -> prefix "com/example/"
                recursive.add(pattern.substring(0, pattern.length() - 2));
            } else if (pattern.endsWith("/*") && pattern.indexOf('*') == pattern.length() - 1) {
                // e.g. "com/example/*" -> prefix "com/example/"
                single.add(pattern.substring(0, pattern.length() - 1));
            } else if (pattern.indexOf('*') == -1) {
                exact.add(pattern);
            } else {
                globs.add(globToPattern(pattern));
            }
        }

        this.matchDefaultPackage = defaultPkg;
        this.exactMatches = Collections.unmodifiableSet(exact);
        this.recursivePrefixes = recursive.toArray(new String[0]);
        this.singleLevelPrefixes = single.toArray(new String[0]);
        this.globPatterns = globs.toArray(new Pattern[0]);
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
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new ClassFilter(parseLines(reader));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read configuration: " + filePath, e);
        }
    }

    /**
     * Parses non-empty, non-comment lines from a reader, stripping inline comments and trimming whitespace.
     */
    static List<String> parseLines(Reader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            int commentIdx = line.indexOf('#');
            if (commentIdx >= 0) {
                line = line.substring(0, commentIdx);
            }
            line = line.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    @Override
    public boolean test(String className) {
        if (className == null || isExcluded(className)) {
            return false;
        }
        if (exactMatches.contains(className)) {
            return true;
        }
        for (int i = 0; i < recursivePrefixes.length; i++) {
            if (className.startsWith(recursivePrefixes[i])) {
                return true;
            }
        }
        for (int i = 0; i < singleLevelPrefixes.length; i++) {
            String prefix = singleLevelPrefixes[i];
            if (className.startsWith(prefix) && className.indexOf('/', prefix.length()) == -1) {
                return true;
            }
        }
        if (matchDefaultPackage && className.indexOf('/') == -1) {
            return true;
        }
        for (int i = 0; i < globPatterns.length; i++) {
            if (globPatterns[i].matcher(className).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Also applied to patterns, to warn about entries that can never match. */
    static boolean isExcluded(String internalName) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return internalName.startsWith(AGENT_PACKAGE)
                && internalName.indexOf('/', AGENT_PACKAGE.length()) == -1;
    }

    static Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        int len = glob.length();
        int i = 0;
        while (i < len) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < len && glob.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i += 2;
                } else {
                    sb.append("[^/]*");
                    i++;
                }
            } else if ("\\.[]{}()+?^$|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }
}
