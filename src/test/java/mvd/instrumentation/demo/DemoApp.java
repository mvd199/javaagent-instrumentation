package mvd.instrumentation.demo;

/**
 * Demo runner class. Not listed in demo.cfg, so its own methods
 * (including main) are not instrumented, keeping console output clean.
 */
public class DemoApp {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("        DurationAgent Demonstration App           ");
        System.out.println("==================================================");
        System.out.println("Threshold: 50 milliseconds\n");

        DemoService service = new DemoService();

        System.out.println("[Step 1] Calling fastTask() (~10ms)...");
        service.fastTask();
        System.out.println("         -> Completed! (Under the 50ms threshold, so it is not recorded)\n");

        System.out.println("[Step 2] Calling slowQuery() (~120ms)...");
        service.slowQuery();
        System.out.println("         -> Completed!\n");

        System.out.println("[Step 3] Calling nestedWorkflow() (contains heavyComputation ~80ms)...");
        service.nestedWorkflow();
        System.out.println("         -> Completed!\n");

        System.out.println("==================================================");
        System.out.println("             Demo Finished Successfully           ");
        System.out.println("==================================================");
    }
}
