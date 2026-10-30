package mvd.instrumentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoreRecorderTest {
    private static long startedMillisAgo(long millis) {
        return System.nanoTime() - millis * 1_000_000L;
    }

    @Test
    public void keepsCallsAboveThresholdWithTheirDetails() {
        CoreRecorder recorder = new CoreRecorder(50, 10);

        recorder.record("a.B.slow()", startedMillisAgo(80), 2);

        Report report = recorder.snapshot();
        assertEquals(1, report.records.size());
        ReportRecord call = report.records.get(0);
        assertEquals("a.B.slow()", call.method);
        assertEquals(Thread.currentThread().getName(), call.thread);
        assertEquals(2, call.depth);
        assertTrue("duration should be about 80 ms but was " + call.durationMs, call.durationMs >= 80);
        assertTrue(call.timestampMillis <= System.currentTimeMillis());
        assertEquals(50, report.thresholdMs);
    }

    @Test
    public void ignoresCallsAtOrBelowThreshold() {
        CoreRecorder recorder = new CoreRecorder(50, 10);

        recorder.record("a.B.fast()", System.nanoTime(), 0);

        assertTrue(recorder.snapshot().records.isEmpty());
        assertEquals(0, recorder.snapshot().dropped);
    }

    @Test
    public void dropsAndCountsCallsBeyondTheCap() {
        CoreRecorder recorder = new CoreRecorder(0, 2);

        for (int i = 0; i < 5; i++) {
            recorder.record("a.B.m" + i + "()", startedMillisAgo(10), 0);
        }

        Report report = recorder.snapshot();
        assertEquals(2, report.records.size());
        assertEquals(3, report.dropped);
    }

    @Test
    public void acceptsConcurrentRecordsWithoutExceedingTheCap() throws Exception {
        CoreRecorder recorder = new CoreRecorder(0, 100);
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    recorder.record("a.B.m()", startedMillisAgo(10), 0);
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        Report report = recorder.snapshot();
        assertEquals(100, report.records.size());
        assertEquals(700, report.dropped);
    }
}
