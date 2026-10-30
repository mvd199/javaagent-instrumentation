package mvd.instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.function.Predicate;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;

/** Injects {@link CoreCallTracker} calls around every method of the classes the filter allows. */
final class CoreTransformer implements ClassFileTransformer {
    private static final String TRACKER = CoreCallTracker.class.getName();

    private final Predicate<String> allowTransformationPredicate;
    private final ClassPool classPool = ClassPool.getDefault();

    CoreTransformer(Predicate<String> allowTransformationPredicate) {
        this.allowTransformationPredicate = allowTransformationPredicate;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        // A null loader is the bootstrap loader. The injected code calls CoreCallTracker, which is on the
        // system class path where bootstrap classes cannot see it, so it would throw NoClassDefFoundError.
        // CoreClassFilter's excluded prefixes cover most of these too, but not classes added with -Xbootclasspath/a.
        if (loader == null || !allowTransformationPredicate.test(className)) {
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
                AgentLogger.LOG.debug(() -> "Transforming: " + className);
                result = ctClass.toBytecode();
            }
            ctClass.detach();
        } catch (Throwable e) {
            AgentLogger.LOG.error("Failed to transform class: " + className, e);
        }
        return result;
    }

    private static <T extends CtBehavior> void emit(T target) throws CannotCompileException {
        target.insertBefore(TRACKER + ".enter();");
        target.insertAfter(TRACKER + ".exit(\"" + target.getLongName() + "\");", true);
    }
}
