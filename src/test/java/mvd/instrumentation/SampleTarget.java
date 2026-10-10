package mvd.instrumentation;

/** Fixture class instrumented by {@link DurationTransformerTest}. */
public class SampleTarget {
    public int add(int a, int b) {
        return a + b;
    }

    public int outer() {
        return add(1, 2);
    }

    public int recurse(int depth) {
        return depth == 0 ? 0 : 1 + recurse(depth - 1);
    }

    public void fail() {
        throw new IllegalStateException("expected");
    }
}
