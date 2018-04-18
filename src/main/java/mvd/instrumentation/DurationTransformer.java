package mvd.instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;

public class DurationTransformer implements ClassFileTransformer {
    private static BiConsumer<String, Long> registerStartTimeFunction = (s, d) -> { };

    private final Predicate<String> allowTransformationPredicate;
    private final ClassPool classPool = ClassPool.getDefault();

    DurationTransformer(Predicate<String> allowTransformationPredicate, BiConsumer<String, Long> registerFunction) {
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
                System.out.println("Transforming: " + className);
                result = ctClass.toBytecode();
            }
            ctClass.detach();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    private static <T extends CtBehavior> void emit(T target) throws CannotCompileException {
        target.addLocalVariable("__startTime", CtClass.longType);
        target.insertBefore("__startTime = System.nanoTime();");
        target.insertAfter("mvd.instrumentation.DurationTransformer.registerStartTime(\"" + target.getLongName() + "\", __startTime);");
    }

    /** Used by instrumentation code. */
    public static void registerStartTime(final String methodName, final long startTimeNanos) {
        registerStartTimeFunction.accept(methodName, startTimeNanos);
    }
}
