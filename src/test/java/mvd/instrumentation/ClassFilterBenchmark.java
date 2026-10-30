package mvd.instrumentation;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark measuring the execution latency of {@link ClassFilter#test(String)}
 * across common matching scenarios.
 * <ul>
 *   <li>Hit on exact match</li>
 *   <li>Hit on single-level package wildcard</li>
 *   <li>Hit on recursive package wildcard</li>
 *   <li>Miss on excluded JDK class (e.g. java/lang/String)</li>
 *   <li>Miss on sibling package (e.g. com/example2 vs com/example)</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(0)
@State(Scope.Benchmark)
public class ClassFilterBenchmark {

    private ClassFilter filter;

    private String exactHit;
    private String singleLevelHit;
    private String recursiveHit;
    private String jdkMiss;
    private String siblingMiss;

    @Setup
    public void setup() {
        List<String> patterns = new ArrayList<>();
        // 50 exact class configurations
        for (int i = 0; i < 50; i++) {
            patterns.add("com.example.service.Service" + i);
        }
        // Single-level wildcards
        patterns.add("com.example.web.*");
        patterns.add("com.example.controller.*");
        patterns.add("com.example.api.*");

        // Recursive wildcards
        patterns.add("com.example.data.**");
        patterns.add("com.example.model.**");

        this.filter = new ClassFilter(patterns);

        this.exactHit = "com/example/service/Service25";
        this.singleLevelHit = "com/example/web/UserController";
        this.recursiveHit = "com/example/data/repository/internal/ItemRepositoryImpl";
        this.jdkMiss = "java/lang/String";
        this.siblingMiss = "com/example2/service/Service25";
    }

    @Benchmark
    public void hitExactMatch(Blackhole bh) {
        bh.consume(filter.test(exactHit));
    }

    @Benchmark
    public void hitSingleLevelWildcard(Blackhole bh) {
        bh.consume(filter.test(singleLevelHit));
    }

    @Benchmark
    public void hitRecursiveWildcard(Blackhole bh) {
        bh.consume(filter.test(recursiveHit));
    }

    @Benchmark
    public void missJdkClass(Blackhole bh) {
        bh.consume(filter.test(jdkMiss));
    }

    @Benchmark
    public void missSiblingPackage(Blackhole bh) {
        bh.consume(filter.test(siblingMiss));
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ClassFilterBenchmark.class.getSimpleName())
                .forks(0)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();
        new Runner(opt).run();
    }
}
