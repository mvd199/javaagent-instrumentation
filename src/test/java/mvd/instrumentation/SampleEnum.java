package mvd.instrumentation;

/** Fixture enum, must never be instrumented. */
public enum SampleEnum {
    A, B;

    public String describe() {
        return name();
    }
}
