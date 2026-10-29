package mvd.instrumentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
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
 * Filter that decides whether a given class should be instrumented.
 * <p>
 * Supports:
 * <ul>
 *   <li>Exact match: {@code com.example.Foo}</li>
 *   <li>Single-level package wildcard: {@code com.example.*} (matches direct classes in package)</li>
 *   <li>Recursive prefix wildcard: {@code com.example.**} (matches package and all subpackages)</li>
 *   <li>Default package single-level: {@code *}</li>
 *   <li>Global match: {@code **}</li>
 *   <li>Arbitrary globs: e.g. {@code com.example.*Service}</li>
 *   <li>Blank lines, whitespace trimming, and full/inline comments ({@code # ...})</li>
 * </ul>
 */
public final class ClassFilter implements Predicate<String> {
    private final boolean matchAll;
    private final boolean matchDefaultPackage;
    private final Set<String> exactMatches;
    private final String[] recursivePrefixes;
    private final String[] singleLevelPrefixes;
    private final Pattern[] globPatterns;

    public ClassFilter(Collection<String> patterns) {
        boolean all = false;
        boolean defaultPkg = false;
        Set<String> exact = new HashSet<>();
        List<String> recursive = new ArrayList<>();
        List<String> single = new ArrayList<>();
        List<Pattern> globs = new ArrayList<>();

        if (patterns != null) {
            for (String raw : patterns) {
                if (raw == null) {
                    continue;
                }
                String pattern = raw.trim();
                int commentIdx = pattern.indexOf('#');
                if (commentIdx >= 0) {
                    pattern = pattern.substring(0, commentIdx).trim();
                }
                if (pattern.isEmpty()) {
                    continue;
                }

                // Normalize dots to slashes for JVM internal class name format
                pattern = pattern.replace('.', '/');

                if ("**".equals(pattern)) {
                    all = true;
                } else if ("*".equals(pattern)) {
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
        }

        this.matchAll = all;
        this.matchDefaultPackage = defaultPkg;
        this.exactMatches = Collections.unmodifiableSet(exact);
        this.recursivePrefixes = recursive.toArray(new String[0]);
        this.singleLevelPrefixes = single.toArray(new String[0]);
        this.globPatterns = globs.toArray(new Pattern[0]);
    }

    public static ClassFilter fromPatterns(String... patterns) {
        return new ClassFilter(patterns != null ? Arrays.asList(patterns) : Collections.<String>emptyList());
    }

    public static ClassFilter fromLines(Collection<String> lines) {
        return new ClassFilter(lines);
    }

    public static ClassFilter fromReader(Reader reader) throws IOException {
        return new ClassFilter(parseLines(reader));
    }

    public static ClassFilter fromFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Configuration file can't be null.");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("Configuration file not found: " + file.getPath());
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return fromReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read configuration: " + file.getPath(), e);
        }
    }

    public static ClassFilter fromFile(String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("Agent args can't be null.");
        }
        if ("".equals(filePath)) {
            throw new IllegalArgumentException("Agent args can't be empty.");
        }
        return fromFile(new File(filePath));
    }

    /**
     * Parses non-empty, non-comment lines from a reader, stripping inline comments and trimming whitespace.
     */
    public static List<String> parseLines(Reader reader) throws IOException {
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
        if (className == null) {
            return false;
        }
        if (matchAll) {
            return true;
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
