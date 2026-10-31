package mvd.instrumentation;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoreCallTrackerTest {
    private final List<String> reported = new ArrayList<>();

    @After
    public void uninstallCallback() {
        CoreCallTracker.install((method, start, depth) -> { });
    }

    @Test
    public void exitReportsMethodStartTimeAndDepth() {
        List<Integer> depths = new ArrayList<>();
        CoreCallTracker.install((method, start, depth) -> {
            reported.add(method);
            depths.add(depth);
        });

        CoreCallTracker.enter();
        CoreCallTracker.enter();
        CoreCallTracker.exit("inner");
        CoreCallTracker.exit("outer");

        assertEquals(Arrays.asList("inner", "outer"), reported);
        assertEquals(Arrays.asList(1, 0), depths);
    }

    @Test
    public void exitWithoutEnterIgnoresSentinel() {
        CoreCallTracker.install((method, start, depth) -> reported.add(method));

        CoreCallTracker.exit("unmatched.Method");

        assertTrue("Callback should not be invoked when popping sentinel", reported.isEmpty());
    }

    @Test
    public void longStackReturnsSentinelWhenEmpty() {
        CoreCallTracker.LongStack stack = new CoreCallTracker.LongStack();
        assertEquals(0, stack.size());
        assertEquals(32, stack.capacity());
        assertEquals(CoreCallTracker.LongStack.SENTINEL, stack.pop());
        assertEquals(0, stack.size());
    }

    @Test
    public void longStackPushesAndPopsInLifoOrder() {
        CoreCallTracker.LongStack stack = new CoreCallTracker.LongStack();
        stack.push(100L);
        stack.push(200L);
        stack.push(300L);

        assertEquals(3, stack.size());
        assertEquals(300L, stack.pop());
        assertEquals(200L, stack.pop());
        assertEquals(100L, stack.pop());
        assertEquals(CoreCallTracker.LongStack.SENTINEL, stack.pop());
    }

    @Test
    public void longStackExpandsCapacityBeyondInitial32() {
        CoreCallTracker.LongStack stack = new CoreCallTracker.LongStack();
        for (int i = 0; i < 40; i++) {
            stack.push((long) i);
        }

        assertTrue("Capacity should expand beyond 32", stack.capacity() > 32);
        assertEquals(40, stack.size());

        for (int i = 39; i >= 0; i--) {
            assertEquals((long) i, stack.pop());
        }
        assertEquals(CoreCallTracker.LongStack.SENTINEL, stack.pop());
    }
}
