package mvd.instrumentation;

/** Fixture class instrumented by {@link DurationTransformerTest}. */
public class SampleTarget {
    public int add(int a, int b) {
        return a + b;
    }

    public void fail() {
        throw new IllegalStateException("expected");
    }
}
