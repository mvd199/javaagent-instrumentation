package mvd.instrumentation;

import java.util.Arrays;

/**
 * Per-thread call timing for the code {@link CoreTransformer} injects. Injected code in application classes can only
 * reach public static methods, so this state is static; {@link DurationAgent} installs the callback once at startup.
 */
public final class CoreCallTracker {
    private static final ThreadLocal<LongStack> START_TIMES = ThreadLocal.withInitial(LongStack::new);
    private static volatile CoreCallback callback = (method, startNanos, depth) -> { };

    private CoreCallTracker() {
    }

    static void install(CoreCallback callback) {
        CoreCallTracker.callback = callback;
    }

    /** Called by injected code at method entry. */
    public static void enter() {
        START_TIMES.get().push(System.nanoTime());
    }

    /**
     * Called by injected code when the method returns or throws. The depth passed to the callback is the number of
     * instrumented frames still active on this thread, so an outermost call reports 0.
     */
    public static void exit(String method) {
        LongStack startTimes = START_TIMES.get();
        long startNanos = startTimes.pop();
        if (startNanos != LongStack.SENTINEL) {
            try {
                callback.onCallFinished(method, startNanos, startTimes.size());
            } catch (Throwable ignored) {
                // Runs in a finally-style handler: must never replace the instrumented method's outcome.
            }
        }
    }

    static final class LongStack {
        static final long SENTINEL = Long.MIN_VALUE;
        private static final int INITIAL_CAPACITY = 32;
        private long[] elements;
        private int size;

        LongStack() {
            this.elements = new long[INITIAL_CAPACITY];
        }

        void push(long val) {
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, elements.length * 2);
            }
            elements[size++] = val;
        }

        /** Returns {@link #SENTINEL} when the stack is empty. */
        long pop() {
            if (size > 0) {
                return elements[--size];
            }
            return SENTINEL;
        }

        int size() {
            return size;
        }

        int capacity() {
            return elements.length;
        }
    }
}
