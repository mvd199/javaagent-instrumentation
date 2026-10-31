package mvd.instrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CoreTransformerTest {
    private static final String TARGET = "mvd/instrumentation/SampleTarget";
    private static final ClassLoader LOADER = CoreTransformerTest.class.getClassLoader();

    private final List<String> reportedMethods = new ArrayList<>();
    private final List<Long> reportedStartTimes = new ArrayList<>();

    @Before
    public void resetRecorded() {
        reportedMethods.clear();
        reportedStartTimes.clear();
    }

    @After
    public void uninstallCallback() {
        CoreCallTracker.install((method, start, depth) -> { });
    }

    @Test
    public void returnsNullForClassesOutsideWhitelist() throws Exception {
        CoreTransformer transformer = transformerAllowing(name -> false);

        assertNull(transformer.transform(LOADER, TARGET, null, null, classBytes(TARGET)));
    }

    @Test
    public void returnsNullForBootstrapClasses() throws Exception {
        CoreTransformer transformer = transformerAllowing(name -> true);

        assertNull(transformer.transform(null, TARGET, null, null, classBytes(TARGET)));
    }

    @Test
    public void returnsNullForInterface() throws Exception {
        String name = "mvd/instrumentation/SampleInterface";

        assertNull(transformerAllowing(name::equals).transform(LOADER, name, null, null, classBytes(name)));
    }

    @Test
    public void returnsNullForEnum() throws Exception {
        String name = "mvd/instrumentation/SampleEnum";

        assertNull(transformerAllowing(name::equals).transform(LOADER, name, null, null, classBytes(name)));
    }

    @Test
    public void returnsModifiedBytecodeForWhitelistedClass() throws Exception {
        byte[] original = classBytes(TARGET);

        byte[] transformed = transformerAllowing(TARGET::equals).transform(LOADER, TARGET, null, null, original);

        assertNotNull(transformed);
        assertTrue("Bytecode should grow after instrumentation", transformed.length > original.length);
    }

    @Test
    public void instrumentedMethodKeepsItsBehaviourAndReportsToCallback() throws Exception {
        Object target = instrumentedInstance();

        Object result = target.getClass().getMethod("add", int.class, int.class).invoke(target, 2, 3);

        assertEquals(5, result);
        assertEquals(1, reportedMethods.size());
        assertEquals("mvd.instrumentation.SampleTarget.add(int,int)", reportedMethods.get(0));
        long elapsedNanos = System.nanoTime() - reportedStartTimes.get(0);
        assertTrue("Start time should be a past System.nanoTime() value", elapsedNanos >= 0);
    }

    @Test
    public void reportsDurationWhenMethodThrows() throws Exception {
        Object target = instrumentedInstance();

        try {
            target.getClass().getMethod("fail").invoke(target);
        } catch (java.lang.reflect.InvocationTargetException expected) {
            // the fixture throws on purpose
        }

        assertEquals(1, reportedMethods.size());
        assertEquals("mvd.instrumentation.SampleTarget.fail()", reportedMethods.get(0));
    }

    @Test
    public void nestedCallsReportTheirOwnStartTimesInnerFirst() throws Exception {
        Object target = instrumentedInstance();

        target.getClass().getMethod("outer").invoke(target);

        assertEquals(2, reportedMethods.size());
        assertEquals("mvd.instrumentation.SampleTarget.add(int,int)", reportedMethods.get(0));
        assertEquals("mvd.instrumentation.SampleTarget.outer()", reportedMethods.get(1));
        assertTrue("Outer must start no later than inner", reportedStartTimes.get(1) <= reportedStartTimes.get(0));
    }

    @Test
    public void recursiveCallsReportOncePerInvocation() throws Exception {
        Object target = instrumentedInstance();

        target.getClass().getMethod("recurse", int.class).invoke(target, 5);

        assertEquals(6, reportedMethods.size());
        for (int i = 1; i < reportedStartTimes.size(); i++) {
            assertTrue("Deeper calls start later, so they are reported earlier",
                    reportedStartTimes.get(i) <= reportedStartTimes.get(i - 1));
        }
    }

    @Test
    public void callbackDepthCountsActiveInstrumentedFrames() throws Exception {
        List<Integer> depths = new ArrayList<>();
        Object target = instrumentedInstance((method, start, depth) -> depths.add(depth));

        target.getClass().getMethod("recurse", int.class).invoke(target, 2);

        // Completion order is innermost first: recurse(0) has 2 active parents, the outermost call has none.
        assertEquals(java.util.Arrays.asList(2, 1, 0), depths);
    }

    @Test
    public void depthIsRestoredAfterAnException() throws Exception {
        List<Integer> depths = new ArrayList<>();
        Object target = instrumentedInstance((method, start, depth) -> depths.add(depth));

        try {
            target.getClass().getMethod("fail").invoke(target);
        } catch (java.lang.reflect.InvocationTargetException expected) {
            // the fixture throws on purpose
        }
        target.getClass().getMethod("add", int.class, int.class).invoke(target, 2, 3);

        assertEquals(java.util.Arrays.asList(0, 0), depths);
    }

    @Test
    public void throwingCallbackDoesNotChangeReturnValue() throws Exception {
        Object target = instrumentedInstance(throwingCallback());

        Object result = target.getClass().getMethod("add", int.class, int.class).invoke(target, 2, 3);

        assertEquals(5, result);
    }

    @Test
    public void throwingCallbackDoesNotReplaceOriginalException() throws Exception {
        Object target = instrumentedInstance(throwingCallback());

        try {
            target.getClass().getMethod("fail").invoke(target);
            fail("Expected the fixture exception");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }
    }

    private static CoreTransformer transformerAllowing(Predicate<String> whitelist) {
        return new CoreTransformer(whitelist);
    }

    private static CoreCallback throwingCallback() {
        return (method, start, depth) -> {
            throw new RuntimeException("callback failure");
        };
    }

    private Object instrumentedInstance() throws Exception {
        return instrumentedInstance((method, start, depth) -> {
            reportedMethods.add(method);
            reportedStartTimes.add(start);
        });
    }

    private Object instrumentedInstance(CoreCallback callback) throws Exception {
        CoreCallTracker.install(callback);
        byte[] transformed = new CoreTransformer(TARGET::equals)
                .transform(LOADER, TARGET, null, null, classBytes(TARGET));
        assertNotNull(transformed);
        return new SingleClassLoader(TARGET.replace('/', '.'), transformed, getClass().getClassLoader())
                .loadClass(TARGET.replace('/', '.'))
                .getDeclaredConstructor()
                .newInstance();
    }

    private static byte[] classBytes(String internalName) throws IOException {
        try (InputStream in = CoreTransformerTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            assertNotNull("Class file not found: " + internalName, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    /** Defines one class from the given bytes, delegating everything else to the parent. */
    private static class SingleClassLoader extends ClassLoader {
        private final String className;
        private final byte[] bytes;

        SingleClassLoader(String className, byte[] bytes, ClassLoader parent) {
            super(parent);
            this.className = className;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(className)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : defineClass(name, bytes, 0, bytes.length);
            }
        }
    }
}
