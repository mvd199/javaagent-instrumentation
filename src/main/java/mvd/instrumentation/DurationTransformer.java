package mvd.instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.function.Predicate;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;

public class DurationTransformer implements ClassFileTransformer {
    private static DurationCallback registerStartTimeFunction = (method, startNanos) -> { };

    private final Predicate<String> allowTransformationPredicate;
    private final ClassPool classPool = ClassPool.getDefault();

    DurationTransformer(Predicate<String> allowTransformationPredicate, DurationCallback registerFunction) {
        this.allowTransformationPredicate = allowTransformationPredicate;
        registerStartTimeFunction = registerFunction;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!allowTransformationPredicate.test(className)) {
            return null;
        }

        byte[] result = null;
        try {
            CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
            if (ctClass.isFrozen()) {
                return null;
            }

            if (ctClass.isPrimitive() || ctClass.isArray() || ctClass.isAnnotation()
                    || ctClass.isEnum() || ctClass.isInterface()) {
                return null;
            }

            boolean isClassModified = false;
            for(CtMethod method: ctClass.getDeclaredMethods()) {
                if (method.isEmpty()) {
                    continue;
                }
                emit(method);
                isClassModified = true;
            }

            if (isClassModified) {
                if (AgentLogger.isDebugEnabled()) {
                    AgentLogger.debug("Transforming: " + className);
                }
                result = ctClass.toBytecode();
            }
            ctClass.detach();
        } catch (Throwable e) {
            AgentLogger.error("Failed to transform class: " + className, e);
        }
        return result;
    }

    private static final ThreadLocal<LongStack> TIMESTAMPS = ThreadLocal.withInitial(LongStack::new);

    private static <T extends CtBehavior> void emit(T target) throws CannotCompileException {
        target.insertBefore("mvd.instrumentation.DurationTransformer.pushStartTime();");
        target.insertAfter("mvd.instrumentation.DurationTransformer.popAndRegisterStartTime(\"" + target.getLongName() + "\");", true);
    }

    /** Used by instrumentation code to push start timestamp at method entry. */
    public static void pushStartTime() {
        TIMESTAMPS.get().push(System.nanoTime());
    }

    /** Used by instrumentation code to pop timestamp and invoke callback in finally block. */
    public static void popAndRegisterStartTime(final String methodName) {
        long startTime = TIMESTAMPS.get().pop();
        if (startTime != LongStack.SENTINEL) {
            try {
                registerStartTimeFunction.onCallFinished(methodName, startTime);
            } catch (Throwable ignored) {
                // Runs in a finally-style handler: must never replace the instrumented method's outcome.
            }
        }
    }

    /**
     * Number of instrumented frames still active on the current thread. Called from the callback,
     * which runs after the finished method was popped, so an outermost call reports 0.
     */
    static int currentDepth() {
        return TIMESTAMPS.get().size();
    }

    static class LongStack {
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
