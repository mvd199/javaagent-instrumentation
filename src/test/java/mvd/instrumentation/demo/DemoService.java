package mvd.instrumentation.demo;

/**
 * Sample service used to demonstrate DurationAgent instrumentation.
 * Listed in demo.cfg so all its methods are instrumented.
 */
public class DemoService {

    /** Fast execution (< 50ms): should NOT be logged by the agent. */
    public void fastTask() throws InterruptedException {
        Thread.sleep(10);
    }

    /** Slow execution (~120ms > 50ms): SHOULD be logged by the agent. */
    public void slowQuery() throws InterruptedException {
        Thread.sleep(120);
    }

    /**
     * Nested workflow that calls a fast task and a slow task.
     * Demonstrates how parent and child durations are reported.
     */
    public void nestedWorkflow() throws InterruptedException {
        fastTask();
        heavyComputation();
    }

    /** Internal slow task called by nestedWorkflow. */
    public void heavyComputation() throws InterruptedException {
        Thread.sleep(80);
    }
}
